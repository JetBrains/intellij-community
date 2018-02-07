/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.idea;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;

/**
 * @author Mike
 */
public class IdeaLogger extends Log4jBasedLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;  // when not null, holds the first of errors that occurred

  private static final ApplicationInfoProvider ourApplicationInfoProvider;
  private static final String ourCompilationTimestamp;
  private static final ThrowableRenderer ourThrowableRenderer;

  static {
    ourApplicationInfoProvider = () -> {
      ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
      return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
    };

    String stamp = null;
    URL resource = Logger.class.getResource("/.compilation-timestamp");
    if (resource != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
        String s = reader.readLine();
        if (s != null) {
          stamp = s.trim();
        }
      }
      catch (IOException ignored) { }
    }
    ourCompilationTimestamp = stamp;

    ourThrowableRenderer = t -> {
      String[] lines = DefaultThrowableRenderer.render(t);
      int maxStackSize = 1024;
      int maxExtraSize = 256;
      if (lines.length > maxStackSize + maxExtraSize) {
        String[] res = new String[maxStackSize + maxExtraSize + 1];
        System.arraycopy(lines, 0, res, 0, maxStackSize);
        res[maxStackSize] = "\t...";
        System.arraycopy(lines, lines.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
        return res;
      }
      return lines;
    };
  }

  public static @Nullable String getOurCompilationTimestamp() {
    return ourCompilationTimestamp;
  }

  public static @NotNull ThrowableRenderer getThrowableRenderer() {
    return ourThrowableRenderer;
  }

  IdeaLogger(@NotNull org.apache.log4j.Logger logger) {
    super(logger);
    LoggerRepository repository = myLogger.getLoggerRepository();
    if (repository instanceof ThrowableRendererSupport) {
      ((ThrowableRendererSupport)repository).setThrowableRenderer(ourThrowableRenderer);
    }
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
  public void error(String message, @Nullable Throwable t, @NotNull Attachment... attachments) {
    String trace = ExceptionUtil.getThrowableText(t != null ? t : new Throwable());
    myLogger.error(LogMessageEx.createEvent(message, trace, attachments));
  }

  @Override
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    if (t instanceof ControlFlowException) {
      myLogger.error(message, new Throwable("Control-flow exceptions (like " + t.getClass().getSimpleName() + ") should never be logged", t));
      ExceptionUtil.rethrow(t);
    }

    String detailString = StringUtil.join(details, "\n");

    if (ourErrorsOccurred == null) {
      String s = message != null && !message.isEmpty() ? "Error message is '" + message + "'" : "";
      String mess = "Logger errors occurred. See IDEA logs for details. " + s;
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourErrorsOccurred = new Exception(mess + (!detailString.isEmpty() ? "\nDetails: " + detailString : ""), t);
    }

    myLogger.error(message + (!detailString.isEmpty() ? "\nDetails: " + detailString : ""), t);
    logErrorHeader();
  }

  private void logErrorHeader() {
    String info = ourApplicationInfoProvider.getInfo();

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
    if (application != null && application.isComponentsCreated() && !application.isDisposed()) {
      String lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
        myLogger.error("Last Action: " + lastPreformedActionId);
      }

      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      if (commandProcessor != null) {
        String currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
          myLogger.error("Current Command: " + currentCommandName);
        }
      }
    }
  }
}