/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2019 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.core.utils;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Stopwatch;
import edp.core.exception.ServerException;
import edp.core.model.MailContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static edp.core.consts.Consts.PATTERN_EMAIL_FORMAT;

@Component
@Slf4j
public class MailUtils {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("{spring.mail.fromAddress:}")
    private String fromAddress;

    @Value("${spring.mail.nickname}")
    private String nickName;


    public void sendMail(MailContent mailContent) throws ServerException {
        Stopwatch watch = Stopwatch.createStarted();
        if (mailContent == null) {
            throw new ServerException("Mail content is null");
        }

        String from = StringUtils.isEmpty(fromAddress) ? mailUsername : fromAddress;

        String displayName = nickName;
        if (!StringUtils.isEmpty(mailContent.getFrom())) {
            Matcher matcher = PATTERN_EMAIL_FORMAT.matcher(mailContent.getFrom());
            if (!matcher.find()) {
                log.info("Unknown email sending address: {}", mailContent.getFrom());
                throw new ServerException("Unknown email sending address: " + mailContent.getFrom());
            }
            from = mailContent.getFrom();
        }

        if (!StringUtils.isEmpty(mailContent.getNickName())) {
            displayName = mailContent.getNickName();
        }

        if (StringUtils.isEmpty(mailContent.getSubject())) {
            log.info("Email subject cannot be EMPTY");
            throw new ServerException("Email subject cannot be EMPTY");
        }

        if (null == mailContent.getTo() || mailContent.getTo().length < 1) {
            log.info("Email receiving address(to) cannot be EMPTY");
            throw new ServerException("Email receiving address cannot be EMPTY");
        }

        boolean multipart = false, html = false;
        String content = "<html></html>";
        boolean emptyAttachments = CollectionUtils.isEmpty(mailContent.getAttachments());

        switch (mailContent.getMailContentType()) {
            case TEXT:
                if (StringUtils.isEmpty(mailContent.getContent()) && emptyAttachments) {
                    throw new ServerException("Mail content cannot be EMPTY");
                }
                if (!emptyAttachments) {
                    multipart = true;
                }
                content = mailContent.getContent();
                break;
            case HTML:
                if (StringUtils.isEmpty(mailContent.getHtmlContent()) && emptyAttachments) {
                    throw new ServerException("Mail content cannot be EMPTY");
                }
                if (!emptyAttachments) {
                    multipart = true;
                }
                html = true;
                content = mailContent.getHtmlContent() + "<br/>";
                break;
            case TEMPLATE:
                if (StringUtils.isEmpty(mailContent.getTemplate()) && emptyAttachments) {
                    throw new ServerException("Mail content cannot be EMPTY");
                }
                Context context = new Context();
                if (!CollectionUtils.isEmpty(mailContent.getTemplateContent())) {
                    mailContent.getTemplateContent().forEach(context::setVariable);
                }
                content = templateEngine.process(mailContent.getTemplate(), context);
                html = true;
                multipart = true;
                break;
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, multipart);

            messageHelper.setFrom(from, displayName);
            messageHelper.setSubject(mailContent.getSubject());
            messageHelper.setTo(mailContent.getTo());
            if (null != mailContent.getCc() && mailContent.getCc().length > 0) {
                messageHelper.setCc(mailContent.getCc());
            }
            if (null != mailContent.getBcc() && mailContent.getBcc().length > 0) {
                messageHelper.setBcc(mailContent.getBcc());
            }
            messageHelper.setText(content, html);

            if (!emptyAttachments) {
                mailContent.getAttachments().forEach(attachment -> {
                    try {
                        if (attachment.isImage()) {
                            messageHelper.addInline(attachment.getName(), attachment.getFile());
                        } else {
                            messageHelper.addAttachment(attachment.getName(), attachment.getFile());
                        }
                    } catch (MessagingException e) {
                        log.warn(e.getMessage());
                    }
                });
            }

            javaMailSender.send(message);
            log.info("MailUtil.sendMail sending: MailContent: {}, cost: {}", JSONObject.toJSONString(mailContent), watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (MessagingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
            throw new ServerException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
        }
    }
}
