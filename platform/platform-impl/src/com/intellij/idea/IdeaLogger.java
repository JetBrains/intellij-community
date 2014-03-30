/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.idea;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.ApplicationInfoProvider;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * @author Mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class IdeaLogger extends Logger {
  private static ApplicationInfoProvider ourApplicationInfoProvider = getIdeaInfoProvider();

  public static String ourLastActionId = "";

  /** If not null - it means that errors occurred and it is the first of them. */
  public static Exception ourErrorsOccurred;

  public static String getOurCompilationTimestamp() {
    return ourCompilationTimestamp;
  }

  private static String ourCompilationTimestamp;

  @NonNls private static final String COMPILATION_TIMESTAMP_RESOURCE_NAME = "/.compilation-timestamp";

  static {
    InputStream stream = Logger.class.getResourceAsStream(COMPILATION_TIMESTAMP_RESOURCE_NAME);
    if (stream != null) {
      try {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(stream));
        try {
          String s = reader.readLine();
          if (s != null) {
            ourCompilationTimestamp = s.trim();
          }
        }
        finally {
          reader.close();
        }
      }
      catch (IOException ignored) { }
    }
  }

  private final org.apache.log4j.Logger myLogger;

  IdeaLogger(org.apache.log4j.Logger logger) {
    myLogger = logger;
  }

  @Override
  public void error(Object message) {
    if (message instanceof IdeaLoggingEvent) {
      myLogger.error(message);
    }
    else {
      super.error(message);
    }
  }

  @Override
  public void error(@NonNls String message, Attachment... attachments) {
    myLogger.error(LogMessageEx.createEvent(message, DebugUtil.currentStackTrace(), attachments));
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isDebugEnabled();
  }

  @Override
  public void debug(String message) {
    myLogger.debug(message);
  }

  @Override
  public void debug(Throwable t) {
    myLogger.debug("", t);
  }

  @Override
  public void debug(@NonNls String message, Throwable t) {
    myLogger.debug(message, t);
  }

  @Override
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    if (t instanceof ProcessCanceledException) {
      myLogger.error(new Throwable("Do not log ProcessCanceledException").initCause(t));
      throw (ProcessCanceledException)t;
    }

    if (t != null && t.getClass().getName().contains("ReparsedSuccessfullyException")) {
      myLogger.error(new Throwable("Do not log ReparsedSuccessfullyException").initCause(t));
      throw (RuntimeException)t;
    }

    String detailString = StringUtil.join(details, "\n");

    if (ourErrorsOccurred == null) {
      String s = message != null && !message.isEmpty() ? "Error message is '" + message + "'" : "";
      String mess = "Logger errors occurred. See IDEA logs for details. " + s;
      ourErrorsOccurred = new Exception(mess + (!detailString.isEmpty() ? "\nDetails: " + detailString : ""), t);
    }

    myLogger.error(message + (!detailString.isEmpty() ? "\nDetails: " + detailString : ""), t);
    logErrorHeader();
  }

  private void logErrorHeader() {
    final String info = ourApplicationInfoProvider.getInfo();

    if (info != null) {
      myLogger.error(info);
    }

    if (ourCompilationTimestamp != null) {
      myLogger.error("Internal version. Compiled " + ourCompilationTimestamp);
    }

    myLogger.error("JDK: " + System.getProperties().getProperty("java.version", "unknown"));
    myLogger.error("VM: " + System.getProperties().getProperty("java.vm.name", "unknown"));
    myLogger.error("Vendor: " + System.getProperties().getProperty("java.vendor", "unknown"));
    myLogger.error("OS: " + System.getProperties().getProperty("os.name", "unknown"));

    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    if (application != null && application.isComponentsCreated()) {
      final String lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
        myLogger.error("Last Action: " + lastPreformedActionId);
      }

      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      if (commandProcessor != null) {
        final String currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
          myLogger.error("Current Command: " + currentCommandName);
        }
      }
    }
  }

  @Override
  public void info(String message) {
    myLogger.info(message);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    myLogger.info(message, t);
  }

  @Override
  public void warn(@NonNls String message, @Nullable Throwable t) {
    myLogger.warn(message, t);
  }

  public static void setApplicationInfoProvider(ApplicationInfoProvider aProvider) {
    ourApplicationInfoProvider = aProvider;
  }

  private static ApplicationInfoProvider getIdeaInfoProvider() {
    return new ApplicationInfoProvider() {
      @Override
      public String getInfo() {
        final ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
        return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
      }
    };
  }

  @Override
  public void setLevel(Level level) {
    myLogger.setLevel(level);
  }
}
