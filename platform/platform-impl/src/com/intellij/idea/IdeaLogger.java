// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.plugins.PluginUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class IdeaLogger extends JulLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  /**
   * We try to report exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}) judiciously,
   * so that instead of polluting the log with hundreds of identical {@link com.intellij.openapi.diagnostic.Logger#error(Throwable) LOG.errors}
   * we print the error message and the stacktrace once in a while.
   * <p>
   * {@code -Didea.logger.exception.expiration.minutes=5} means to forget about this particular exception if it didn't occur for five minutes.
   * <p>
   * To disable the "mute frequent exceptions" feature completely, specify {@code -Didea.logger.exception.expiration.minutes=0}
   */
  private static final int EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES = Integer.getInteger("idea.logger.exception.expiration.minutes", 8*60);

  // must be as a separate class to avoid initialization as part of start-up (file logger configuration)
  private static final class MyCache {
    private static final Cache<@NotNull String, @NotNull AtomicInteger> cache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Math.max(EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES, 0), TimeUnit.MINUTES)
      .build();

    private static @NotNull AtomicInteger getOrCreate(int hash, @NotNull Throwable t) {
      return cache.get(hash + ":" + t, __ -> new AtomicInteger());
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

    var hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    var counter = MyCache.getOrCreate(hash, t);
    var occurrences = counter.incrementAndGet();
    return occurrences != 1;
  }

  private static void reportToFus(@NotNull Throwable t) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return;
    }

    var app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode() && !app.isDisposed()) {
      var pluginUtil = PluginUtil.getInstance();
      if (pluginUtil != null) {
        var pluginId = pluginUtil.findPluginId(t);
        var kind = DefaultIdeaErrorLogger.getOOMErrorKind(t);
        LifecycleUsageTriggerCollector.onError(pluginId, t, kind);
      }
    }
  }

  static boolean isMutingFrequentExceptionsEnabled() {
    return EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES > 0;
  }

  private static final Supplier<String> ourApplicationInfoProvider = () -> {
    var info = ApplicationInfoImpl.getShadowInstance();
    return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
  };

  IdeaLogger(@NotNull Logger logger) {
    super(logger);
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (isTooFrequentException(t)) return;

    if (attachments.length == 0) {
      logSevere(message, t);
    }
    else if (t != null) {
      logSevere(message, new RuntimeExceptionWithAttachments(t, attachments));
    }
    else {
      logSevere(message, new RuntimeExceptionWithAttachments(new Throwable(), attachments));
    }
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

  private void doLogError(String message, @Nullable Throwable t, String... details) {
    if (t != null && shouldRethrow(t)) {
      logSevere(message, ensureNotControlFlow(t));
      ExceptionUtil.rethrow(t);
    }

    var detailString = String.join("\n", details);
    if (!detailString.isEmpty()) {
      detailString = "\nDetails: " + detailString;
    }

    if (ourErrorsOccurred == null) {
      var mess = "Logger errors occurred. See IDEA logs for details. " +
                 (message == null || message.isEmpty() ? "" : "Error message is '" + message + "'");
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourErrorsOccurred = new Exception(mess + detailString, t);
    }
    logSevere(message + detailString, t);
  }

  private void logErrorHeader(@Nullable Throwable t) {
    logSevere(ourApplicationInfoProvider.get());

    var properties = System.getProperties();
    logSevere("JDK: " + properties.getProperty("java.version", "unknown") +
              "; VM: " + properties.getProperty("java.vm.name", "unknown") +
              "; Vendor: " + properties.getProperty("java.vendor", "unknown"));
    logSevere("OS: " + properties.getProperty("os.name", "unknown"));

    // do not use getInstance here - container maybe already disposed
    if (t != null && PluginManagerCore.arePluginsInitialized()) {
      var plugin = PluginManagerCore.getPlugin(PluginUtilImpl.doFindPluginId(t));
      if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
        logSevere("Plugin to blame: " + plugin.getName() + " version: " + plugin.getVersion());
      }
    }

    var application = ApplicationManagerEx.getApplicationEx();
    if (application != null && application.isComponentCreated() && !application.isDisposed()) {
      var lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
        logSevere("Last Action: " + lastPreformedActionId);
      }

      var commandProcessor = application.getServiceIfCreated(CommandProcessor.class);
      if (commandProcessor != null) {
        var currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
          logSevere("Current Command: " + currentCommandName);
        }
      }
    }
  }
}
