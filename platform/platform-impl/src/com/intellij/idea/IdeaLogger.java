// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.logs.LoggerConfigFromSystemProperties;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.plugins.PluginUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.diagnostic.UnhandledException;
import com.intellij.openapi.diagnostic.UnhandledExceptionKind;
import com.intellij.openapi.diagnostic.UnhandledReportSinkService;
import com.intellij.openapi.diagnostic.UnhandledReportSinkService.PluginExceptionReportData;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Component;
import java.awt.Graphics;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class IdeaLogger extends JulLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  /// We try to report exceptions thrown from frequently called methods (e.g. [Component#paint(Graphics)]) judiciously,
  /// so that instead of polluting the log with hundreds of identical [`LOG.errors`][com.intellij.openapi.diagnostic.Logger#error(Throwable)]
  /// we print the error message and the stacktrace once in a while.
  ///
  /// `-Didea.logger.exception.expiration.minutes=5` means to forget about this particular exception if it didn't occur for five minutes.
  ///
  /// To disable the "mute frequent exceptions" feature completely, specify `-Didea.logger.exception.expiration.minutes=0`
  private static final int EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES =
    Integer.getInteger("idea.logger.exception.expiration.minutes", 8 * 60);

  // must be as a separate class to avoid initialization as part of start-up (file logger configuration)
  private static final class MyCache {
    private static final Cache<@NotNull String, @NotNull AtomicInteger> cache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Math.max(EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES, 0), TimeUnit.MINUTES)
      .build();

    private static @NotNull AtomicInteger getOrCreate(int hash, @NotNull Throwable t) {
      return cache.get(hash + ":" + t, _ -> new AtomicInteger());
    }
  }

  private static final ThreadLocal<Boolean> FUS_RECURSION_GUARD = new ThreadLocal<>();

  public static void dropFrequentExceptionsCaches() {
    MyCache.cache.invalidateAll();
    MyCache.cache.cleanUp();
  }

  private boolean isTooFrequentException(@Nullable Throwable t) {
    if (t == null ||
        !isMutingFrequentExceptionsEnabled() ||
        !LoadingState.COMPONENTS_LOADED.isOccurred() ||
        isDebugEnabled() ||
        isTraceEnabled()) {
      return false;
    }

    // `processUnhandledException` is the only place that builds an `UnhandledException`, so the trace of a
    // wrapper is almost constant. Count the real cause instead, or this method mutes every unhandled
    // exception after the first one. See IJPL-254578.
    var realCause = UnhandledException.unwrapIfUnhandled(t).getRealCause();

    var hash = ThrowableInterner.computeAccurateTraceHashCode(realCause);
    var counter = MyCache.getOrCreate(hash, realCause);
    var occurrences = counter.incrementAndGet();
    if (isFascinatingNumber(occurrences)) {
      var msg = shortenErrorMessage(realCause.getMessage());
      warn("Suppressed a frequent exception logged for the " + occurrences + (occurrences == 2 ? "nd" : "th") + " time: " + msg);
    }
    return occurrences != 1;
  }

  /// 2, 5, 10, 20, 50, 100, ...
  private static boolean isFascinatingNumber(int number) {
    if (number <= 1) return false;
    while (number % 10 == 0) number /= 10;
    return number == 1 || number == 2 || number == 5;
  }

  private static String shortenErrorMessage(@Nullable String message) {
    if (message == null) return "null";
    int newLine = message.indexOf('\n');
    message = message.substring(0, newLine != -1 ? newLine : message.length());
    return StringUtil.shortenTextWithEllipsis(message, 300, 0);
  }

  private static void reportToFus(@NotNull Throwable realCause, @NotNull UnhandledExceptionKind unhandledExceptionKind) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred() || FUS_RECURSION_GUARD.get() != null) {
      return;
    }

    FUS_RECURSION_GUARD.set(true);

    try {
      var app = ApplicationManager.getApplication();
      if (app != null && !app.isUnitTestMode() && !app.isDisposed()) {
        var pluginUtil = PluginUtil.getInstance();
        if (pluginUtil != null) {
          var pluginId = pluginUtil.findPluginId(realCause);
          var kind = DefaultIdeaErrorLogger.getOOMErrorKind(realCause);
          LifecycleUsageTriggerCollector.onError(pluginId, realCause, unhandledExceptionKind, kind);
          if (pluginId != null) {
            var sinkService = UnhandledReportSinkService.getInstance();
            if (sinkService != null) { // might be null in CLI utils
              sinkService.report(new PluginExceptionReportData(pluginId, realCause));
            }
          }
        }
      }
    }
    catch (Exception _) {
    }
    finally {
      FUS_RECURSION_GUARD.remove();
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
    if (LoggerConfigFromSystemProperties.hasLogLevelSystemPropertiesConfigured) {
      var level = LoggerConfigFromSystemProperties.getLevelFromSystemProperties(logger.getName());
      if (level != null && (logger.getLevel() == null || level.intValue() < logger.getLevel().intValue())) {
        logger.setLevel(level);
      }
    }
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
    // Count the throwable that a caller gave, and count it once. `withAttachments` builds a new throwable,
    // and the hash reads the trace of that new throwable only. It cannot tell two causes apart. See IJPL-254578.
    if (isTooFrequentException(t)) return;

    doError(message, t, attachments, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /// Wraps the cause, so that a log handler finds the attachments on it.
  /// The kind travels apart, so this method builds no `UnhandledException`. See IJPL-254578.
  private static @NotNull Throwable withAttachments(@Nullable Throwable realCause, Attachment @NotNull [] attachments) {
    var cause = realCause == null ? new Throwable() : realCause;
    return new RuntimeExceptionWithAttachments(ensureNotControlFlow(cause), attachments);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (isTooFrequentException(t)) return;

    doError(message, t, Attachment.EMPTY_ARRAY, details);
  }

  /// Splits an `UnhandledException`, writes the log, rethrows a control-flow exception and reports to FUS.
  /// It counts no frequent exception, because each public overload counts once. See IJPL-254578.
  private void doError(String message,
                       @Nullable Throwable t,
                       Attachment @NotNull [] attachments,
                       String @NotNull [] details) {
    // Split once, and let the wrapper end here. The log writer, FUS and the rethrow all need the real cause.
    // See IJPL-254578.
    var unwrapped = t == null ? null : UnhandledException.unwrapIfUnhandled(t);
    var realCause = unwrapped == null ? null : unwrapped.getRealCause();
    var kind = unwrapped == null ? UnhandledExceptionKind.HANDLED : unwrapped.getUnhandledExceptionKind();

    var detailString = String.join("\n", details);
    if (!detailString.isEmpty()) {
      detailString = "\nDetails: " + detailString;
    }

    if (ourErrorsOccurred == null) {
      var mess = "Logger errors occurred. See IDEA logs for details. " +
                 (message == null || message.isEmpty() ? "" : "Error message is '" + message + "'");
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourErrorsOccurred = new Exception(mess + detailString, realCause);
    }

    // The log writer gets the real cause, and the kind travels beside it in an `IdeaLogRecord`.
    // A handler therefore needs no knowledge of the wrapper. See IJPL-254578.
    var written = attachments.length == 0 ? ensureNotControlFlow(realCause) : withAttachments(realCause, attachments);
    logSevere(message + detailString, written, kind);
    logErrorHeader(realCause);

    // The wrapper can hold a control-flow exception, and the caller must get that exception back.
    if (realCause != null && shouldRethrow(realCause)) {
      ExceptionUtil.rethrow(realCause);
    }

    if (realCause != null) {
      reportToFus(realCause, kind);
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
      var pluginSet = PluginManagerCore.getPluginSetOrNull();
      var idAndPlugin = pluginSet == null ? null : PluginUtils.findPlugin(t, pluginSet);
      var id = idAndPlugin == null ? null : idAndPlugin.getFirst();
      var plugin = idAndPlugin == null ? null : idAndPlugin.getSecond();
      if (id != null && (plugin == null || PluginUtils.isUpdateable(plugin))) {
        logSevere(plugin == null
                  ? "Plugin to blame: " + id.getIdString()
                  : "Plugin to blame: " + plugin.getName() + " version: " + plugin.getVersion());
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
