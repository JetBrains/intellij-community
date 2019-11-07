// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public enum LoadingState {
  BOOTSTRAP("bootstrap"),
  LAF_INITIALIZED("LaF is initialized"),
  COMPONENTS_REGISTERED("app component registered"),
  CONFIGURATION_STORE_INITIALIZED("app store initialized"),
  COMPONENTS_LOADED("app component loaded"),
  PROJECT_OPENED("project opened"),
  INDEXING_FINISHED("indexing finished");

  final String displayName;

  private static boolean CHECK_LOADING_PHASE;
  private static Set<Throwable> stackTraces;

  LoadingState(@NotNull String displayName) {
    this.displayName = displayName;
  }

  @NotNull
  static Logger getLogger() {
    return Logger.getInstance(LoadingState.class);
  }

  @ApiStatus.Internal
  public static void setStrictMode() {
    CHECK_LOADING_PHASE = true;
  }

  public void checkOccurred() {
    if (!CHECK_LOADING_PHASE) {
      return;
    }

    LoadingState currentState = StartUpMeasurer.currentState.get();
    if (currentState.ordinal() >= ordinal() || isKnownViolator()) {
      return;
    }

    logStateError(currentState);
  }

  private synchronized void logStateError(@NotNull LoadingState currentState) {
    Throwable t = new Throwable();
    if (stackTraces == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      stackTraces = new THashSet<>(new TObjectHashingStrategy<Throwable>() {
        @Override
        public int computeHashCode(Throwable throwable) {
          return fingerprint(throwable).hashCode();
        }

        @Override
        public boolean equals(Throwable o1, Throwable o2) {
          return o1 == o2 || o1 != null && o2 != null && fingerprint(o1).equals(fingerprint(o2));
        }

        private String fingerprint(Throwable throwable) {
          StringBuilder sb = new StringBuilder();
          for (StackTraceElement traceElement : throwable.getStackTrace()) {
            sb.append(traceElement.getClassName()).append(traceElement.getMethodName());
          }
          return sb.toString();
        }
      });
    }

    if (!stackTraces.add(t)) {
      return;
    }

    getLogger().error("Should be called at least in the state " + this + ", the current state is: " + currentState + "\n" +
                      "Current violators count: " + stackTraces.size() + "\n\n",
                      t);
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

  public boolean isOccurred() {
    return StartUpMeasurer.currentState.get().ordinal() >= ordinal();
  }
}