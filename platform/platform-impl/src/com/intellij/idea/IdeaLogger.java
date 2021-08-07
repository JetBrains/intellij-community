// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.LogMessage;
import com.intellij.diagnostic.VMOptions;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.plugins.PluginUtilImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class IdeaLogger extends Log4jBasedLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  /**
   * We try to report exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}) judiciously,
   * so that instead of polluting the log with hundreds of identical {@link com.intellij.openapi.diagnostic.Logger#error(Throwable) LOG.errors}
   * we print the error message and the stacktrace once in a while.
   *
   * "-Didea.logger.report.every.nth.exception=10" means print the exception stacktrace to the log the first time it occurred,
   *     and then, for each 10-th occurrence of this very same exception, print "this exception was reported n times already".
   *  "-Didea.logger.exception.expiration.minutes=5" means to forget about this particular exception if it didn't occur for five minutes.
   *
   *  To disable this "mute frequent exceptions" feature completely specify "-Didea.logger.exception.expiration.minutes=0"
   */
  private static final String REPORT_EVERY_NTH_FREQUENT_EXCEPTION_PROPERTY = "idea.logger.report.every.nth.exception";
  final int REPORT_EVERY_NTH_FREQUENT_EXCEPTION = Integer.getInteger(REPORT_EVERY_NTH_FREQUENT_EXCEPTION_PROPERTY, 10);
  private static final int EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES = Integer.getInteger("idea.logger.exception.expiration.minutes", 5);

  static void setMutedExceptionFrequency(String frequency) {
    System.setProperty(REPORT_EVERY_NTH_FREQUENT_EXCEPTION_PROPERTY, frequency);
  }

  // must be as a separate class to avoid initialization as part of start-up (file logger configuration)
  private static final class MyCache {
    private static final Cache<@NotNull String, @NotNull AtomicInteger> cache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Math.max(EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES, 0), TimeUnit.MINUTES)
      .build();

    @NotNull
    private static AtomicInteger getOrCreate(int hash, @NotNull Throwable t) {
      return cache.get(hash+":"+t, __ -> new AtomicInteger());
    }
  }

  public static void dropFrequentExceptionsCaches() {
    MyCache.cache.invalidateAll();
    MyCache.cache.cleanUp();
  }

  private boolean isTooFrequentException(@Nullable Throwable t, @NotNull Level level) {
    if (t == null || !isMutingFrequentExceptionsEnabled() || !LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return false;
    }

    int hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    AtomicInteger counter = MyCache.getOrCreate(hash, t);
    int occurrences = counter.incrementAndGet();
    if (occurrences == 1) {
      return false;
    }

    if (occurrences > 1 && occurrences % REPORT_EVERY_NTH_FREQUENT_EXCEPTION == 0) {
      myLogger.log(level, getExceptionWasAlreadyReportedNTimesMessage(t, occurrences));
    }

    return true;
  }

  @NotNull
  static String getExceptionWasAlreadyReportedNTimesMessage(@NotNull Throwable t, int occurrences) {
    return "Exception '" + t + "' was reported " + occurrences + " times";
  }

  private static void reportToFus(@NotNull Throwable t) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return;
    }

    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode() && !app.isDisposed()) {
      PluginUtil pluginUtil = PluginUtil.getInstance();
      if (pluginUtil != null) {
        PluginId pluginId = pluginUtil.findPluginId(t);
        VMOptions.MemoryKind kind = DefaultIdeaErrorLogger.getOOMErrorKind(t);
        LifecycleUsageTriggerCollector.onError(pluginId, t, kind);
      }
    }
  }

  static boolean isMutingFrequentExceptionsEnabled() {
    return EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES > 0;
  }

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
    if (isTooFrequentException(t, Level.ERROR)) return;
    myLogger.error(LogMessage.createEvent(t != null ? t : new Throwable(), message, attachments));
    if (t != null) {
      reportToFus(t);
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (isTooFrequentException(t, Level.WARN)) return;
    super.warn(message, ensureNotControlFlow(t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (isTooFrequentException(t, Level.ERROR)) return;
    doLogError(message, t, details);
    logErrorHeader(t);
    if (t != null) {
      reportToFus(t);
    }
  }

  private void doLogError(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (t instanceof ControlFlowException) {
      myLogger.error(message, ensureNotControlFlow(t));
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

    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null && application.isComponentCreated() && !application.isDisposed()) {
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