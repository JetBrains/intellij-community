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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

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

  private boolean isTooFrequentException(@Nullable Throwable t) {
    if (t == null || !isMutingFrequentExceptionsEnabled() || !LoadingState.COMPONENTS_LOADED.isOccurred() || isDebugEnabled() || isTraceEnabled()) {
      return false;
    }

    var hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    var counter = MyCache.getOrCreate(hash, t);
    var occurrences = counter.incrementAndGet();
    if (isFascinatingNumber(occurrences)) {
      warn("Suppressed a frequent exception logged for the " + occurrences + (occurrences == 2 ? "nd" : "th") + " time: " +
           shortenErrorMessage(t.getMessage()));
    }
    return occurrences != 1;
  }

  /**
   * 2, 5, 10, 20, 50, 100, ...
   */
  private static boolean isFascinatingNumber(int number) {
    if (number <= 1) return false;
    while (number % 10 == 0) number /= 10;
    return number == 1 || number == 2 || number == 5;
  }

  private static @NotNull String shortenErrorMessage(@Nullable String message) {
    if (message == null) return "null";
    int newLine = message.indexOf('\n');
    message = message.substring(0, newLine != -1 ? newLine : message.length());
    return StringUtil.shortenTextWithEllipsis(message, 300, 0);
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

  @VisibleForTesting
  @ApiStatus.Internal
  public static boolean isMutingFrequentExceptionsEnabled() {
    return EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES > 0;
  }

  private static final Supplier<String> ourApplicationInfoProvider = () -> {
    var info = ApplicationInfoImpl.getShadowInstance();
    return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
  };

  @VisibleForTesting
  @ApiStatus.Internal
  public IdeaLogger(@NotNull Logger logger) {
    super(logger);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    if (isTooFrequentException(t)) return;
    super.info(message, ensureNotControlFlow(t));
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (isTooFrequentException(t)) return;
    super.warn(message, ensureNotControlFlow(t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (isTooFrequentException(t)) return;

    Throwable errorWithAttachment;
    if (attachments.length == 0) {
      errorWithAttachment = t;
    }
    else if (t != null) {
      errorWithAttachment = new RuntimeExceptionWithAttachments(ensureNotControlFlow(t), attachments);
    }
    else {
      errorWithAttachment = new RuntimeExceptionWithAttachments(new Throwable(), attachments);
    }

    error(message, errorWithAttachment);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (isTooFrequentException(t)) return;

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

    logSevere(message + detailString, ensureNotControlFlow(t));
    logErrorHeader(t);

    if (t != null && shouldRethrow(t)) {
      ExceptionUtil.rethrow(t);
    }

    if (t != null) {
      reportToFus(t);
    }
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
