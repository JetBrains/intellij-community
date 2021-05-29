// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.VMOptions;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.DelegatingLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MutedLogger extends DelegatingLogger<Logger> {
  private static final int EXPIRATION = Integer.getInteger("ide.muted.error.logger.expiration", 5);
  private static final int FREQUENCY = Integer.getInteger("ide.muted.error.logger.frequency", 10);

  private static final class LoggerWithCounter {
    private final MutedLogger logger;
    private final AtomicInteger counter = new AtomicInteger();

    private LoggerWithCounter(MutedLogger logger) {
      this.logger = logger;
    }
  }

  // must be as a separate class to avoid initialization as part of start-up (file logger configuration)
  private static final class MyCache {
    private static final Cache<@NotNull String, @NotNull LoggerWithCounter> cache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Math.max(EXPIRATION, 0), TimeUnit.MINUTES)
      .removalListener((@Nullable String key, @Nullable LoggerWithCounter value, RemovalCause cause) -> {
        if (key != null && value != null) {
          int errorIndex = key.indexOf(':');
          String error = key.substring(0, errorIndex);
          String hash = key.substring(errorIndex + 1, key.indexOf(':', errorIndex + 1));
          value.logger.logOccurrences(Boolean.parseBoolean(error), hash, value.counter.get());
        }
      })
      .build();
  }

  public static void dropCaches() {
    MyCache.cache.invalidateAll();
    MyCache.cache.cleanUp();
  }

  @Contract("_ -> new")
  public static @NotNull Logger of(@NotNull Logger delegate) {
    return new MutedLogger(delegate);
  }

  public static boolean isEnabled() {
    return EXPIRATION > 0;
  }

  private static void reportToFus(@NotNull Throwable t) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode() && !application.isDisposed()) {
      PluginId pluginId = PluginUtil.getInstance().findPluginId(t);
      VMOptions.MemoryKind kind = DefaultIdeaErrorLogger.getOOMErrorKind(t);
      LifecycleUsageTriggerCollector.onError(pluginId, t, kind);
    }
  }

  private MutedLogger(Logger delegate) {
    super(delegate);
  }

  private void logOccurrences(boolean error, String hash, int occurrences) {
    if (occurrences > 1) {
      log(error, "Exception with the following hash '" + hash + "' was reported " + occurrences + " times");
    }
  }

  private void log(boolean error, @NotNull String message) {
    if (error) {
      if (myDelegate instanceof IdeaLogger) {
        ((IdeaLogger)myDelegate).error(false, message, null);
      }
      else {
        myDelegate.error(message, (Throwable)null);
      }
    }
    else {
      myDelegate.warn(message);
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (shouldBeReported(false, t)) {
      myDelegate.warn(message, t);
    }
  }

  @Override
  public void error(Object message) {
    if (message instanceof IdeaLoggingEvent) {
      Throwable t = ((IdeaLoggingEvent)message).getThrowable();
      if (!shouldBeReported(true, t)) {
        return;
      }
    }
    myDelegate.error(message);
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (shouldBeReported(true, t)) {
      myDelegate.error(message, t, attachments);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (shouldBeReported(true, t)) {
      myDelegate.error(message, t, details);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t) {
    if (shouldBeReported(true, t)) {
      myDelegate.error(message, t);
    }
  }

  @Override
  public void error(@NotNull Throwable t) {
    if (shouldBeReported(true, t)) {
      myDelegate.error(t);
    }
  }

  private boolean shouldBeReported(boolean error, @Nullable Throwable t) {
    if (t == null || !LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return true;
    }

    int hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    String key = error + ":" + hash + ":" + t;
    LoggerWithCounter holder = MyCache.cache.get(key, __ -> new LoggerWithCounter(this));
    if (holder.counter.compareAndSet(0, 1)) {
      log(error, "Hash for the following exception is '" + hash + "': " + t);
      return true;
    }

    reportToFus(t);

    int occurrences = holder.counter.incrementAndGet();
    if (occurrences % FREQUENCY == 0) {
      logOccurrences(error, String.valueOf(hash), occurrences);
    }

    return false;
  }
}
