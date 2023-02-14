/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.FixedHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FrequentEventDetector {
  private static final Logger LOG = Logger.getInstance(FrequentEventDetector.class);

  public enum Level {INFO, WARN, ERROR}

  private long myStartedCounting = System.currentTimeMillis();
  private final AtomicInteger myEventsPosted = new AtomicInteger();
  private final AtomicInteger myLastTraceId = new AtomicInteger();
  private final Map<String, Integer> myRecentTraces = new FixedHashMap<>(50);
  private final int myEventCountThreshold;
  private final int myTimeSpanMs;
  private final Level myLevel;
  private static final AtomicInteger disableRequests = new AtomicInteger();

  public FrequentEventDetector(int eventCountThreshold, int timeSpanMs) {
    this(eventCountThreshold, timeSpanMs, Level.INFO);
  }

  public FrequentEventDetector(int eventCountThreshold, int timeSpanMs, @NotNull Level level) {
    myEventCountThreshold = eventCountThreshold;
    myTimeSpanMs = timeSpanMs;
    myLevel = level;
  }

  /**
   * @return an error message to be logged, if the current event is a part of a "frequent"-series, null otherwise
   */
  @Nullable
  private String getMessageOnEvent(@NotNull Object event) {
    if (disableRequests.get() == 0) {
      return manyEventsHappenedInSmallTimeSpan(event);
    }
    return null;
  }

  private String manyEventsHappenedInSmallTimeSpan(@NotNull Object event) {
    int eventsPosted = myEventsPosted.incrementAndGet();
    boolean shouldLog = false;
    if (eventsPosted > myEventCountThreshold) {
      synchronized (myEventsPosted) {
        if (myEventsPosted.get() > myEventCountThreshold) {
          long timeNow = System.currentTimeMillis();
          shouldLog = timeNow - myStartedCounting < myTimeSpanMs;
          myEventsPosted.set(0);
          myStartedCounting = timeNow;
        }
      }
    }
    return shouldLog ? generateMessage(event, eventsPosted) : null;
  }

  @NotNull
  private @NonNls String generateMessage(@NotNull Object event, int eventsPosted) {
    String trace = ExceptionUtil.getThrowableText(new Throwable());
    boolean logTrace;
    int traceId;
    synchronized (myEventsPosted) {
      Integer existingTraceId = myRecentTraces.get(trace);
      logTrace = existingTraceId == null;
      if (logTrace) {
        myRecentTraces.put(trace, traceId = myLastTraceId.incrementAndGet());
      }
      else {
        traceId = existingTraceId;
      }
    }

    return "Too many events posted (" + eventsPosted+")"
           + " #" + traceId + ". Event: '" + event + "'"
           + (logTrace ? "\n" + trace : "");
  }

  public void logMessage(@NotNull String message) {
    if (myLevel == Level.INFO) {
      LOG.info(message);
    }
    else if (myLevel == Level.WARN) {
      LOG.warn(message);
    }
    else {
      LOG.error(message);
    }
  }

  /**
   * Logs a message if the given event is part of a "frequent" series. To just return the message without logging, use {@link #getMessageOnEvent(Object)}
   */
  public void eventHappened(@NotNull Object event) {
    String message = getMessageOnEvent(event);
    if (message != null) {
      logMessage(message);
    }
  }

  @TestOnly
  public static void disableUntil(@NotNull Disposable reenable) {
    disableRequests.incrementAndGet();
    Disposer.register(reenable, () -> disableRequests.decrementAndGet());
  }
}
