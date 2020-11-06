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
    return parse(text, validateRequiredAttributes, true, null);
  }

  public static @Nullable ServiceMessage parse(@NotNull String text, boolean validateRequiredAttributes, boolean logErrorOnParseFailure) {
    return parse(text, validateRequiredAttributes, logErrorOnParseFailure, null);
  }

  public static @Nullable ServiceMessage parse(@NotNull String text, boolean validateRequiredAttributes, boolean logErrorOnParseFailure,
                                               @Nullable String testFrameworkName) {
    if (text.startsWith(ServiceMessage.SERVICE_MESSAGE_START) && text.endsWith(ServiceMessage.SERVICE_MESSAGE_END)) {
      ServiceMessagesParser parser = new ServiceMessagesParser();
      parser.setValidateRequiredAttributes(validateRequiredAttributes);
      MyServiceMessageParserCallback callback = new MyServiceMessageParserCallback(text, logErrorOnParseFailure, testFrameworkName);
      parser.parse(text, callback);
      return callback.getParsedMessage();
    }
    return null;
  }

  private static class MyServiceMessageParserCallback implements ServiceMessageParserCallback {
    private final @NotNull String myText;
    private final boolean myLogErrorOnParseFailure;
    private final @Nullable String myTestFrameworkName;
    private ServiceMessage myMessage;
    private boolean myParseFailed;

    MyServiceMessageParserCallback(@NotNull String text, boolean logErrorOnParseFailure, @Nullable String testFrameworkName) {
      myText = text;
      myLogErrorOnParseFailure = logErrorOnParseFailure;
      myTestFrameworkName = testFrameworkName;
    }

    @Override
    public void regularText(@NotNull String regularText) {
      if (myLogErrorOnParseFailure) {
        LOG.error("Regular text encountered when parsing service message", myText, regularText, getTestFrameworkName());
      }
      myParseFailed = true;
    }

    @Override
    public void serviceMessage(@NotNull ServiceMessage message) {
      if (myMessage != null) {
        if (myLogErrorOnParseFailure) {
          LOG.error("Another service message already parsed", myText, myMessage.asString(), message.asString(), getTestFrameworkName());
        }
        myParseFailed = true;
      }
      myMessage = message;
    }

    @Override
    public void parseException(@NotNull ParseException parseException, @NotNull String parseErrorText) {
      if (myLogErrorOnParseFailure) {
        LOG.error("Failed to parse service message", parseException, parseErrorText, getTestFrameworkName());
      }
      myParseFailed = true;
    }

    private @NotNull String getTestFrameworkName() {
      return myTestFrameworkName == null ? "unknown testFramework" : ("testFramework:" + myTestFrameworkName);
    }

    public @Nullable ServiceMessage getParsedMessage() {
      return myParseFailed ? null : myMessage;
    }
  }
}
