// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.LogMessage;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;
import java.util.function.Supplier;

public final class IdeaLogger extends Log4jBasedLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  private static final Supplier<String> ourApplicationInfoProvider = () -> {
    ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
    return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
  };

  private static final ThrowableRenderer ourThrowableRenderer = t -> {
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

  public static @NotNull ThrowableRenderer getThrowableRenderer() {
    return ourThrowableRenderer;
  }

  IdeaLogger(@NotNull Logger logger) {
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
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    myLogger.error(LogMessage.createEvent(t != null ? t : new Throwable(), message, attachments));
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    super.warn(message, checkException(t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (t instanceof ControlFlowException) {
      myLogger.error(message, checkException(t));
      ExceptionUtil.rethrow(t);
    }

    String detailString = String.join("\n", details);
    if (!detailString.isEmpty()) {
      detailString = "\nDetails: " + detailString;
    }

    if (ourErrorsOccurred == null) {
      String mess = "Logger errors occurred. See IDEA logs for details. " +
                    (message == null || message.isEmpty() ? "" : "Error message is '" + message + "'");
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourErrorsOccurred = new Exception(mess + detailString, t);
    }
    myLogger.error(message + detailString, t);
    logErrorHeader(t);
  }

  private void logErrorHeader(@Nullable Throwable t) {
    myLogger.error(ourApplicationInfoProvider.get());

    Properties properties = System.getProperties();
    myLogger.error("JDK: " + properties.getProperty("java.version", "unknown") +
                   "; VM: " + properties.getProperty("java.vm.name", "unknown") +
                   "; Vendor: " + properties.getProperty("java.vendor", "unknown"));
    myLogger.error("OS: " + properties.getProperty("os.name", "unknown"));

    // do not use getInstance here - container maybe already disposed
    if (t != null && PluginManagerCore.arePluginsInitialized()) {
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginUtilImpl.doFindPluginId(t));
      if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
        myLogger.error("Plugin to blame: " + plugin.getName() + " version: " + plugin.getVersion());
      }
    }

    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    if (application != null && application.getComponentCreated() && !application.isDisposed()) {
      String lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
        myLogger.error("Last Action: " + lastPreformedActionId);
      }

      CommandProcessor commandProcessor = application.getServiceIfCreated(CommandProcessor.class);
      if (commandProcessor != null) {
        String currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
          myLogger.error("Current Command: " + currentCommandName);
        }
      }
    }
  }
}