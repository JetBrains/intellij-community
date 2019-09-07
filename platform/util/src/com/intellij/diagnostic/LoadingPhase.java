// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@ApiStatus.Internal
public enum LoadingPhase {
  BOOTSTRAP("bootstrap"),
  LAF_INITIALIZED("LaF is initialized"),
  SPLASH("splash shown"),
  COMPONENT_REGISTERED("app component registered"),
  CONFIGURATION_STORE_INITIALIZED("app store initialized"),
  COMPONENT_LOADED("app component loaded"),
  PROJECT_OPENED("project opened"),
  INDEXING_FINISHED("indexing finished");

  private final String displayName;

  LoadingPhase(@NotNull String displayName) {
    this.displayName = displayName;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(LoadingPhase.class);
  }

  private static boolean CHECK_LOADING_PHASE;

  public static void setStrictMode() {
    CHECK_LOADING_PHASE = true;
  }

  public static void setCurrentPhase(@NotNull LoadingPhase phase) {
    LoadingPhase old = currentPhase.getAndSet(phase);
    if (old.ordinal() > phase.ordinal()) {
      getLogger().error("New phase " + phase + " cannot be earlier than old " + old);
    }
    logPhaseSet(phase);
  }

  private final static Set<Throwable> stackTraces = new THashSet<>(new TObjectHashingStrategy<Throwable>() {
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
  });

  private final static AtomicReference<LoadingPhase> currentPhase = new AtomicReference<>(BOOTSTRAP);

  public static void compareAndSet(@NotNull LoadingPhase expect, @NotNull LoadingPhase phase) {
    if (currentPhase.compareAndSet(expect, phase)) {
      logPhaseSet(phase);
    }
  }

  private static void logPhaseSet(@NotNull LoadingPhase phase) {
    StartUpMeasurer.addInstantEvent(phase.displayName);

    if (phase.ordinal() >= CONFIGURATION_STORE_INITIALIZED.ordinal()) {
      getLogger().info("Reached the loading phase " + phase);
    }
  }

  public void assertAtLeast() {
    if (!CHECK_LOADING_PHASE) {
      return;
    }

    LoadingPhase currentPhase = LoadingPhase.currentPhase.get();
    if (currentPhase.ordinal() >= ordinal() || isKnownViolator()) {
      return;
    }

    Throwable t = new Throwable();
    synchronized (stackTraces) {
      if (!stackTraces.add(t)) {
        return;
      }

      getLogger().error("Should be called at least at the phase " + this + ", the current phase is: " + currentPhase + "\n" +
                       "Current violators count: " + stackTraces.size() + "\n\n",
                       t);
    }
  }

  private static boolean isKnownViolator() {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      String className = element.getClassName();
      if (className.contains("com.intellij.util.indexing.IndexInfrastructure") || className.contains("com.intellij.psi.impl.search.IndexPatternSearcher")) {
        return true;
      }
    }
    return false;
  }

  public static boolean isStartupComplete() {
    return INDEXING_FINISHED.isComplete();
  }

  public boolean isComplete() {
    return currentPhase.get().ordinal() >= ordinal();
  }
}