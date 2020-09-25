// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageParserCallback;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessagesParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceMessageUtil {
  private static final Logger LOG = Logger.getInstance(ServiceMessageUtil.class);

  private ServiceMessageUtil() {
  }

  public static @Nullable ServiceMessage parse(@NotNull String text, boolean validateRequiredAttributes) {
    if (text.startsWith(ServiceMessage.SERVICE_MESSAGE_START) && text.endsWith(ServiceMessage.SERVICE_MESSAGE_END)) {
      ServiceMessagesParser parser = new ServiceMessagesParser();
      parser.setValidateRequiredAttributes(validateRequiredAttributes);
      AtomicReference<ServiceMessage> messageRef = new AtomicReference<>();
      AtomicBoolean exceptionOccurred = new AtomicBoolean(false);
      parser.parse(text, new ServiceMessageParserCallback() {
        @Override
        public void regularText(@NotNull String regularText) {
          LOG.error("Regular text encountered when parsing service message", text, regularText);
        }

        @Override
        public void serviceMessage(@NotNull ServiceMessage message) {
          if (messageRef.get() != null) {
            LOG.error("Another service message already parsed", text, messageRef.get().asString(), message.asString());
          }
          messageRef.set(message);
        }

        @Override
        public void parseException(@NotNull ParseException parseException, @NotNull String text1) {
          LOG.error("Failed to parse service message", parseException, text1);
          exceptionOccurred.set(true);
        }
      });
      if (!exceptionOccurred.get()) {
        return messageRef.get();
      }
    }
    return null;
  }
}
