// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public enum LoadingPhase {
  BOOTSTRAP,
  SPLASH,
  FRAME_SHOWN,
  PROJECT_OPENED,
  INDEXING_FINISHED;

  private final static Logger LOG = Logger.getInstance(LoadingPhase.class);
  private final static boolean KEEP_IN_MIND_LOADING_PHASE = SystemProperties.getBooleanProperty("idea.keep.in.mind.loading.phase", false);

  private volatile static LoadingPhase myCurrentPhase = BOOTSTRAP;

  private final static AtomicInteger myViolatorCounter = new AtomicInteger();

  public static void setCurrentPhase(LoadingPhase phase) {
    myCurrentPhase = phase;
  }

  public synchronized static void compareAndSet(LoadingPhase expect, LoadingPhase phase) {
    if (myCurrentPhase == expect) {
      setCurrentPhase(phase);
    }
  }

  public static void assertAtLeast(LoadingPhase phase) {
    if (!KEEP_IN_MIND_LOADING_PHASE) return;

    LoadingPhase currentPhase = myCurrentPhase;

    if (currentPhase.compareTo(phase) < 0) {
      if (isKnowViolator()) return;
      int count = myViolatorCounter.incrementAndGet();

      LOG.warn("Should be called at least at phase " + phase + ", the current phase is: " + currentPhase + "\n" +
               "Current violators count: " + count + "\n\n",
               new Throwable()
      );
    }
  }

  private static boolean isKnowViolator() {
    return Arrays.stream(Thread.currentThread().getStackTrace())
      .anyMatch(element -> {
        String className = element.getClassName();
        if ("com.intellij.openapi.application.Preloader".equals(className)) return true;
        if (className.contains("com.intellij.util.indexing.IndexInfrastructure")) return true;

        return false;
      });
  }
}
