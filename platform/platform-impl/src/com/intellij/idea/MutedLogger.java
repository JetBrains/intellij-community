// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MutedLogger extends Logger {

  private static final Cache<String, LoggerWithCounter> ourCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .removalListener((RemovalListener<String, LoggerWithCounter>)(key, value, cause) -> {
      if (key != null && value != null) {
        String hash = Objects.requireNonNull(StringUtil.substringBefore(key, ":"));
        value.logger.logRemoved(Integer.parseInt(hash), value.counter.get());
      }
    })
    .build();

  public static void dropCaches() {
    ourCache.invalidateAll();
    ourCache.cleanUp();
  }

  @SuppressWarnings("NonConstantLogger") protected final Logger myDelegate;

  protected MutedLogger(@NotNull Logger delegate) {
    myDelegate = delegate;
  }

  protected final boolean isAlreadyReported(@Nullable Throwable throwable) {
    if (throwable == null) {
      return false;
    }

    int hash = ThrowableInterner.computeTraceHashCode(throwable);
    String key = hash + ":" + throwable;
    LoggerWithCounter holder = Objects.requireNonNull(ourCache.get(key, __ -> new LoggerWithCounter(this, new AtomicInteger())));
    if (holder.counter.compareAndSet(0, 1)) {
      logAdded(hash, throwable);
      return false;
    }

    logOccurrences(hash, throwable, holder.counter.incrementAndGet());
    return true;
  }

  protected abstract void logAdded(int hash, @NotNull Throwable t);

  protected abstract void logOccurrences(int hash, @NotNull Throwable t, int occurrences);

  protected abstract void logRemoved(int hash, int occurrences);

  @Override
  public boolean isDebugEnabled() {
    return myDelegate.isDebugEnabled();
  }

  @Override
  public void debug(String message) {
    myDelegate.debug(message);
  }

  @Override
  public void debug(@Nullable Throwable t) {
    myDelegate.debug(t);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    myDelegate.debug(message, t);
  }

  @Override
  public void info(String message) {
    myDelegate.info(message);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    myDelegate.info(message, t);
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    myDelegate.warn(message, t);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    myDelegate.error(message, t, details);
  }

  @Override
  public void setLevel(@NotNull Level level) {
    myDelegate.setLevel(level);
  }

  private static final class LoggerWithCounter {

    private final MutedLogger logger;
    private final AtomicInteger counter;

    private LoggerWithCounter(@NotNull MutedLogger logger, @NotNull AtomicInteger counter) {
      this.logger = logger;
      this.counter = counter;
    }
  }
}
