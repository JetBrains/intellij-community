// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiConsumer;

@ApiStatus.Internal
public enum LoadingState {
  BOOTSTRAP("bootstrap"),
  LAF_INITIALIZED("LaF is initialized"),
  COMPONENTS_REGISTERED("app component registered"),
  CONFIGURATION_STORE_INITIALIZED("app store initialized"),
  COMPONENTS_LOADED("app component loaded"),
  APP_STARTED("app started"),
  PROJECT_OPENED("project opened"),
  INDEXING_FINISHED("indexing finished");

  final String displayName;

  private static BiConsumer<String, Throwable> errorHandler;
  private static boolean CHECK_LOADING_PHASE;
  private static Set<Throwable> stackTraces;

  LoadingState(@NotNull String displayName) {
    this.displayName = displayName;
  }

  @Nullable
  static BiConsumer<String, Throwable> getErrorHandler() {
    return errorHandler;
  }

  @ApiStatus.Internal
  public static void setErrorHandler(@NotNull BiConsumer<String, Throwable> errorHandler) {
    LoadingState.errorHandler = errorHandler;
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
    if (currentState.compareTo(this) >= 0 || isKnownViolator()) {
      return;
    }

    logStateError(currentState);
  }

  private synchronized void logStateError(@NotNull LoadingState currentState) {
    Throwable t = new Throwable();
    if (stackTraces == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      stackTraces = new ObjectOpenCustomHashSet<>(new Hash.Strategy<Throwable>() {
        @Override
        public int hashCode(Throwable throwable) {
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

    BiConsumer<String, Throwable> errorHandler = getErrorHandler();
    if (errorHandler != null) {
      errorHandler.accept("Should be called at least in the state " + this + ", the current state is: " + currentState + "\n" +
                          "Current violators count: " + stackTraces.size() + "\n\n",
                          t);
    }
  }

  private static boolean isKnownViolator() {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      String className = element.getClassName();
      if (className.contains("com.intellij.util.indexing.IndexInfrastructure")
          || className.contains("com.intellij.psi.impl.search.IndexPatternSearcher")
          || className.contains("com.jetbrains.performancePlugin.ProjectLoaded")
          || className.contains("com.jetbrains.python.conda.InstallCondaUtils")) {
        return true;
      }
    }
    return false;
  }

  public boolean isOccurred() {
    return StartUpMeasurer.currentState.get().compareTo(this) >= 0;
  }
}