// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class IdeaLogger extends JulLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  /**
   * We try to report exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}) judiciously,
   * so that instead of polluting the log with hundreds of identical {@link com.intellij.openapi.diagnostic.Logger#error(Throwable) LOG.errors}
   * we print the error message and the stacktrace once in a while.
   *
   *  "-Didea.logger.exception.expiration.minutes=5" means to forget about this particular exception if it didn't occur for five minutes.
   *
   *  To disable this "mute frequent exceptions" feature completely specify "-Didea.logger.exception.expiration.minutes=0"
   */
  private static final int EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES = Integer.getInteger("idea.logger.exception.expiration.minutes", 8*60);

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

  private static boolean isTooFrequentException(@Nullable Throwable t) {
    if (t == null || !isMutingFrequentExceptionsEnabled() || !LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return false;
    }

    int hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    AtomicInteger counter = MyCache.getOrCreate(hash, t);
    int occurrences = counter.incrementAndGet();
    return occurrences != 1;
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

  IdeaLogger(@NotNull java.util.logging.Logger logger) {
    super(logger);
  }

  @Override
  public void error(Object message) {
    if (message instanceof IdeaLoggingEvent) {
       myLogger.log(Level.SEVERE, "{0}", message);
    }
    else {
      super.error(message);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (isTooFrequentException(t)) return;
    myLogger.log(Level.SEVERE, "{0}", LogMessage.createEvent(t != null ? t : new Throwable(), message, attachments));
    if (t != null) {
      reportToFus(t);
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (isTooFrequentException(t)) return;
    super.warn(message, ensureNotControlFlow(t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (isTooFrequentException(t)) return;
    doLogError(message, t, details);
    logErrorHeader(t);
    if (t != null) {
      reportToFus(t);
    }
  }

  private void doLogError(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (t instanceof ControlFlowException) {
      myLogger.log(Level.SEVERE, message, ensureNotControlFlow(t));
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
    myLogger.log(Level.SEVERE, message + detailString, t);
  }

  private void logErrorHeader(@Nullable Throwable t) {
    myLogger.severe(ourApplicationInfoProvider.get());

    Properties properties = System.getProperties();
    myLogger.severe("JDK: " + properties.getProperty("java.version", "unknown") +
                    "; VM: " + properties.getProperty("java.vm.name", "unknown") +
                    "; Vendor: " + properties.getProperty("java.vendor", "unknown"));
    myLogger.severe("OS: " + properties.getProperty("os.name", "unknown"));

    // do not use getInstance here - container maybe already disposed
    if (t != null && PluginManagerCore.arePluginsInitialized()) {
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginUtilImpl.doFindPluginId(t));
      if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
         myLogger.severe("Plugin to blame: " + plugin.getName() + " version: " + plugin.getVersion());
      }
    }

    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null && application.isComponentCreated() && !application.isDisposed()) {
      String lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
         myLogger.severe("Last Action: " + lastPreformedActionId);
      }

      CommandProcessor commandProcessor = application.getServiceIfCreated(CommandProcessor.class);
      if (commandProcessor != null) {
        String currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
           myLogger.severe("Current Command: " + currentCommandName);
        }
      }
    }
  }
}