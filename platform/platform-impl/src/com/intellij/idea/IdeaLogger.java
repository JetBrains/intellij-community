/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
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
@SuppressWarnings("HardCodedStringLiteral")
public class IdeaLogger extends Log4jBasedLogger {
  private static ApplicationInfoProvider ourApplicationInfoProvider = getIdeaInfoProvider();

  public static String ourLastActionId = "";

  /** If not null - it means that errors occurred and it is the first of them. */
  public static Exception ourErrorsOccurred;

  public static String getOurCompilationTimestamp() {
    return ourCompilationTimestamp;
  }

  private static final String ourCompilationTimestamp;

  @NonNls private static final String COMPILATION_TIMESTAMP_RESOURCE_NAME = "/.compilation-timestamp";

  private static final ThrowableRenderer ourThrowableRenderer = t -> {
    String[] defaultRes = DefaultThrowableRenderer.render(t);
    int maxStackSize = 1024;
    int maxExtraSize = 256;
    if (defaultRes.length > maxStackSize + maxExtraSize) {
      String[] res = new String[maxStackSize + maxExtraSize + 1];
      System.arraycopy(defaultRes, 0, res, 0, maxStackSize);
      res[maxStackSize] = "\t...";
      System.arraycopy(defaultRes, defaultRes.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
      return res;
    }
    return defaultRes;
  };

  static {
    InputStream stream = Logger.class.getResourceAsStream(COMPILATION_TIMESTAMP_RESOURCE_NAME);
    String stamp = null;
    if (stream != null) {
      try {
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(stream))) {
          String s = reader.readLine();
          if (s != null) {
            stamp = s.trim();
          }
        }
      }
      catch (IOException ignored) { }
    }
    ourCompilationTimestamp = stamp;
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
  public void error(@NonNls String message, @NotNull Attachment... attachments) {
    myLogger.error(LogMessageEx.createEvent(message, DebugUtil.currentStackTrace(), attachments));
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
    if (application != null && application.isComponentsCreated() && !application.isDisposed()) {
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

  @NotNull
  public static ThrowableRenderer getThrowableRenderer() {
    return ourThrowableRenderer;
  }

  public static void setApplicationInfoProvider(@NotNull ApplicationInfoProvider aProvider) {
    ourApplicationInfoProvider = aProvider;
  }

  @NotNull
  private static ApplicationInfoProvider getIdeaInfoProvider() {
    return () -> {
      final ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
      return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
    };
  }
}
