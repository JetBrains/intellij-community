// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * Logger wrapper that ignores repeating logging attempts if they are done inside specified throttling interval
 * (see ignoreRepeatedMessagesInMs ctor arg)
 */
public class ThrottledLogger {
  @NotNull
  private final Logger logger;
  /**
   * Ignore (i.e. skip logging) subsequent messages with same key if they come during that period after the first message.
   */
  private final long ignoreRepeatedMessagesInMs;

  private final AtomicLong lastLoggedAtMsHolder = new AtomicLong(0);

  public ThrottledLogger(final @NotNull Logger logger,
                         final long ignoreRepeatedMessagesInMs) {
    this.logger = requireNonNull(logger, "logger");
    if (ignoreRepeatedMessagesInMs < 0) {
      throw new IllegalArgumentException("ignoreRepeatedMessagesInMs(=" + ignoreRepeatedMessagesInMs + ") must be >=0");
    }
    this.ignoreRepeatedMessagesInMs = ignoreRepeatedMessagesInMs;
  }

  @NotNull
  public Logger logger() {
    return logger;
  }

  public void debug(final String message) {
    debug(message, null);
  }

  public void debug(final String message,
                    final @Nullable Throwable t) {
    final long nowMs = System.currentTimeMillis();
    long lastLoggedAt = lastLoggedAtMsHolder.get();
    if (lastLoggedAt + ignoreRepeatedMessagesInMs < nowMs) {
      logger.debug(message, t);

      forwardLastLogged(nowMs, lastLoggedAt);
    }
  }


  public void info(String message) {
    info(message, null);
  }

  public void info(final String message,
                   final @Nullable Throwable t) {
    final long nowMs = System.currentTimeMillis();
    long lastLoggedAt = lastLoggedAtMsHolder.get();
    if (lastLoggedAt + ignoreRepeatedMessagesInMs < nowMs) {
      logger.info(message, t);

      forwardLastLogged(nowMs, lastLoggedAt);
    }
  }

  public void warn(final String message) {
    warn(message, null);
  }

  public void warn(final String message,
                   final @Nullable Throwable t) {
    final long nowMs = System.currentTimeMillis();
    long lastLoggedAt = lastLoggedAtMsHolder.get();
    if (lastLoggedAt + ignoreRepeatedMessagesInMs < nowMs) {
      logger.warn(message, t);

      forwardLastLogged(nowMs, lastLoggedAt);
    }
  }

  public void error(final String message) {
    error(message, null);
  }

  public void error(final String message,
                    final @Nullable Throwable t) {
    final long nowMs = System.currentTimeMillis();
    long lastLoggedAt = lastLoggedAtMsHolder.get();
    if (lastLoggedAt + ignoreRepeatedMessagesInMs < nowMs) {
      logger.error(message, t);

      forwardLastLogged(nowMs, lastLoggedAt);
    }
  }

  /**
   * Forward lastLogged timestamp to at least nowMs value, or do nothing if lastLogged is already >=nowMs
   */
  private void forwardLastLogged(final long nowMs,
                                 long lastLoggedAt) {
    while (!lastLoggedAtMsHolder.compareAndSet(lastLoggedAt, nowMs)) {
      lastLoggedAt = lastLoggedAtMsHolder.get();
      if (lastLoggedAt >= nowMs) {
        break; // -> somebody else forwarded lastLoggedAtMsHolder for us
      }
    }
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
