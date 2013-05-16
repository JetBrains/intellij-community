/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.util.ExceptionUtil;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
public class FrequentEventDetector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diagnostic.FrequentEventDetector");
  private long myStartedCounting = System.currentTimeMillis();
  private final AtomicInteger myEventsPosted = new AtomicInteger();
  private final int myEventCountThreshold;
  private final int myTimeSpanMs;

  public FrequentEventDetector(int eventCountThreshold, int timeSpanMs) {
    myEventCountThreshold = eventCountThreshold;
    myTimeSpanMs = timeSpanMs;
  }

  public void eventHappened() {
    if (myEventsPosted.incrementAndGet() > myEventCountThreshold) {
      synchronized (myEventsPosted) {
        if (myEventsPosted.get() > myEventCountThreshold) {
          long timeNow = System.currentTimeMillis();
          if (timeNow - myStartedCounting < myTimeSpanMs) {
            LOG.info("Too many events posted\n" + ExceptionUtil.getThrowableText(new Throwable()));
          }
          myEventsPosted.set(0);
          myStartedCounting = timeNow;
        }
      }
    }

  }

}
