// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Logger wrapper that ignores repeating logging attempts if they are done inside specified throttling interval
 * (see ignoreRepeatedMessagesInMs ctor arg).
 *
 * <p><b>Thread-safety:</b> Uses lock-free CAS (compare-and-set) to guarantee exactly one log message
 * per throttle period, even under high concurrent load. Multiple threads racing to log will compete
 * atomically, with only one winner logging and others being throttled.</p>
 */
@ApiStatus.Internal
public final class ThrottledLogger {
  @SuppressWarnings("NonConstantLogger")
  private final @NotNull Logger logger;

  /**
   * Ignore (i.e. skip logging) subsequent messages with same key if they come during that period after the first message.
   */
  private final long ignoreRepeatedMessagesInMs;

  private final AtomicLong lastLoggedAtMsHolder = new AtomicLong(0);

  public ThrottledLogger(@NotNull Logger logger, long ignoreRepeatedMessagesInMs) {
    this.logger = logger;
    if (ignoreRepeatedMessagesInMs < 0) {
      throw new IllegalArgumentException("ignoreRepeatedMessagesInMs(=" + ignoreRepeatedMessagesInMs + ") must be >= 0");
    }
    this.ignoreRepeatedMessagesInMs = ignoreRepeatedMessagesInMs;
  }

  public @NotNull Logger wrappedLogger() {
    return logger;
  }

  public void debug(String message) {
    debug(message, null);
  }

  public void debug(String message, @Nullable Throwable t) {
    if (!logger.isDebugEnabled() || isMuted()) return;
    logger.debug(message, t);
  }

  public void debug(@NotNull Supplier<String> messageSupplier) {
    if (!logger.isDebugEnabled() || isMuted()) return;
    logger.debug(messageSupplier.get());
  }

  public void info(String message) {
    info(message, null);
  }

  public void info(String message, @Nullable Throwable t) {
    if (isMuted()) return;
    logger.info(message, t);
  }

  public void info(@NotNull Supplier<String> messageSupplier) {
    if (isMuted()) return;
    logger.info(messageSupplier.get());
  }

  public void warn(String message) {
    warn(message, null);
  }

  public void warn(String message, @Nullable Throwable t) {
    if (isMuted()) return;
    logger.warn(message, t);
  }

  public void warn(@NotNull Supplier<String> messageSupplier) {
    if (isMuted()) return;
    logger.warn(messageSupplier.get());
  }

  public void error(String message) {
    error(message, null);
  }

  public void error(String message, @Nullable Throwable t) {
    if (isMuted()) return;
    logger.error(message, t);
  }

  public void error(@NotNull Supplier<String> messageSupplier) {
    if (isMuted()) return;
    logger.error(messageSupplier.get());
  }

  private boolean isMuted() {
    if (ignoreRepeatedMessagesInMs == 0) return false;

    long nowMs = System.currentTimeMillis();
    long lastLoggedAt = lastLoggedAtMsHolder.get();

    if (lastLoggedAt + ignoreRepeatedMessagesInMs >= nowMs) {
      return true;
    }

    return !lastLoggedAtMsHolder.compareAndSet(lastLoggedAt, nowMs);
  }

  @Override
  public String toString() {
    return "ThrottledLogger[" +
           "ignoreRepeatedMessagesInMs: " + ignoreRepeatedMessagesInMs +
           ", lastLoggedAtMs:" + lastLoggedAtMsHolder.get() +
           ", wrapped logger:" + logger +
           '}';
  }
}
