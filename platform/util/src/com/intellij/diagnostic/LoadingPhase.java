// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.SystemProperties;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

@ApiStatus.Internal
public enum LoadingPhase {
  BOOTSTRAP,
  SPLASH,
  FRAME_SHOWN,
  PROJECT_OPENED,
  INDEXING_FINISHED;

  private final static Logger LOG = Logger.getInstance(LoadingPhase.class);
  private final static boolean KEEP_IN_MIND_LOADING_PHASE = SystemProperties.getBooleanProperty("idea.keep.in.mind.loading.phase", false);

  public static void setCurrentPhase(LoadingPhase phase) {
    myCurrentPhase = phase;
    LOG.info("Reached " + phase + " loading phase");
  }

  public static final TObjectHashingStrategy<Throwable> THROWABLE_HASHING_STRATEGY = new TObjectHashingStrategy<Throwable>() {
    @Override
    public int computeHashCode(Throwable throwable) {
      return getCollect(throwable).hashCode();
    }

    private String getCollect(Throwable throwable) {
      return Arrays
        .stream(throwable.getStackTrace())
        .map(element -> element.getClassName() + element.getMethodName())
        .collect(Collectors.joining());
    }

    @Override
    public boolean equals(Throwable o1, Throwable o2) {
      if (o1 == o2) return true;
      if (o1 == null || o2 == null) return false;
      return Comparing.equal(getCollect(o1), getCollect(o2));
    }
  };

  private final static THashSet<Throwable> myStackTraces = new THashSet<>(THROWABLE_HASHING_STRATEGY);

  private volatile static LoadingPhase myCurrentPhase = BOOTSTRAP;

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
      Throwable t = new Throwable();

      synchronized (myStackTraces) {
        if (!myStackTraces.add(t)) return;

        LOG.warn("Should be called at least at phase " + phase + ", the current phase is: " + currentPhase + "\n" +
                 "Current violators count: " + myStackTraces.size() + "\n\n",
                 t
        );
      }
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

  public static boolean isStartupComplete() {
    return myCurrentPhase == INDEXING_FINISHED;
  }
}
