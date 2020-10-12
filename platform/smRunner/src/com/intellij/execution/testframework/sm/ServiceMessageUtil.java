// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageParserCallback;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessagesParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;

public class ServiceMessageUtil {
  private static final Logger LOG = Logger.getInstance(ServiceMessageUtil.class);

  private ServiceMessageUtil() {
  }

  public static @Nullable ServiceMessage parse(@NotNull String text, boolean validateRequiredAttributes) {
    if (text.startsWith(ServiceMessage.SERVICE_MESSAGE_START) && text.endsWith(ServiceMessage.SERVICE_MESSAGE_END)) {
      ServiceMessagesParser parser = new ServiceMessagesParser();
      parser.setValidateRequiredAttributes(validateRequiredAttributes);
      MyServiceMessageParserCallback callback = new MyServiceMessageParserCallback(text);
      parser.parse(text, callback);
      return callback.getParsedMessage();
    }
    return null;
  }

  private static class MyServiceMessageParserCallback implements ServiceMessageParserCallback {
    private @NotNull final String myText;
    private ServiceMessage myMessage;
    private boolean myParseFailed;

    MyServiceMessageParserCallback(@NotNull String text) {
      myText = text;
    }

    @Override
    public void regularText(@NotNull String regularText) {
      LOG.error("Regular text encountered when parsing service message", myText, regularText);
    }

    @Override
    public void serviceMessage(@NotNull ServiceMessage message) {
      if (myMessage != null) {
        LOG.error("Another service message already parsed", myText, myMessage.asString(), message.asString());
      }
      myMessage = message;
    }

    @Override
    public void parseException(@NotNull ParseException parseException, @NotNull String parseErrorText) {
      LOG.error("Failed to parse service message", parseException, parseErrorText);
      myParseFailed = true;
    }

    public @Nullable ServiceMessage getParsedMessage() {
      return myParseFailed ? null : myMessage;
    }
  }
}
