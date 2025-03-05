// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@ApiStatus.Internal
public enum LoadingState {
  BOOTSTRAP("bootstrap"),
  COMPONENTS_REGISTERED("app component registered"),
  CONFIGURATION_STORE_INITIALIZED("app store initialized"),
  COMPONENTS_LOADED("app component loaded"),
  /** Application and LaF are ready, but it's too early for the post-startup activities, still. */
  APP_READY("app ready"),
  APP_STARTED("app started"),
  PROJECT_OPENED("project opened");

  private static final AtomicReference<LoadingState> currentState = new AtomicReference<>(BOOTSTRAP);

  final String displayName;

  @SuppressWarnings("StaticNonFinalField")
  public static BiConsumer<String, Throwable> errorHandler;

  private static boolean CHECK_LOADING_PHASE;
  private static Set<ThrowableWrapper> stackTraces;

  LoadingState(@NotNull String displayName) {
    this.displayName = displayName;
  }

  @ApiStatus.Internal
  public static void setStrictMode() {
    CHECK_LOADING_PHASE = true;
  }

  public void checkOccurred() {
    if (!CHECK_LOADING_PHASE) {
      return;
    }

    LoadingState currentState = LoadingState.currentState.get();
    if (currentState.compareTo(this) >= 0 || isKnownViolator()) {
      return;
    }

    logStateError(currentState);
  }

  private synchronized void logStateError(@NotNull LoadingState currentState) {
    Throwable t = new Throwable();
    if (stackTraces == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      stackTraces = new HashSet<>();
    }

    if (!stackTraces.add(new ThrowableWrapper(t))) {
      return;
    }

    BiConsumer<String, Throwable> errorHandler = LoadingState.errorHandler;
    if (errorHandler != null) {
      errorHandler.accept("Should be called at least in the state " + this + ", the current state is: " + currentState + "\n" +
                          "Current violators count: " + stackTraces.size() + "\n\n",
                          t);
    }
  }

  private static final class ThrowableWrapper {
    final Throwable throwable;

    private ThrowableWrapper(Throwable throwable) {
      this.throwable = throwable;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof ThrowableWrapper) {
        Throwable throwable = ((ThrowableWrapper)obj).throwable;
        return this.throwable == throwable || fingerprint(this.throwable).equals(fingerprint(throwable));
      }
      return false;
    }

    @Override
    public int hashCode() {
      return fingerprint(throwable).hashCode();
    }

    private static String fingerprint(Throwable throwable) {
      StringBuilder sb = new StringBuilder();
      for (StackTraceElement traceElement : throwable.getStackTrace()) {
        sb.append(traceElement.getClassName()).append(traceElement.getMethodName());
      }
      return sb.toString();
    }
  }

  private static boolean isKnownViolator() {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      String className = element.getClassName();
      if (className.contains("com.intellij.util.indexing.IndexInfrastructure")
          || className.contains("com.intellij.psi.impl.search.IndexPatternSearcher")
          || className.contains("com.jetbrains.performancePlugin.ProjectLoaded")) {
        return true;
      }
    }
    return false;
  }

  public boolean isOccurred() {
    return currentState.get().compareTo(this) >= 0;
  }

  @ApiStatus.Internal
  public static void setCurrentState(@NotNull LoadingState state) {
    LoadingState old = currentState.getAndSet(state);
    if (old.compareTo(state) > 0) {
      BiConsumer<String, Throwable> errorHandler = LoadingState.errorHandler;
      if (errorHandler != null) {
        errorHandler.accept("New state " + state + " cannot precede old " + old, new Throwable());
      }
    }
  }

  @ApiStatus.Internal
  public static void compareAndSetCurrentState(@NotNull LoadingState expectedState, @NotNull LoadingState newState) {
    currentState.compareAndSet(expectedState, newState);
  }

  @ApiStatus.Internal
  public static void setCurrentStateIfAtLeast(@NotNull LoadingState expectedState, @NotNull LoadingState newState) {
    assert newState.compareTo(expectedState) > 0;

    while (true) {
      LoadingState current = currentState.get();
      if (current.compareTo(expectedState) < 0) {
        // The expected state is not yet reached.
        return;
      }
      if (current.compareTo(newState) >= 0) {
        // The current state is already equal or higher than the one we wanted to set.
        return;
      }
      if (currentState.compareAndSet(current, newState)) {
        // We succeeded in setting the state.
        return;
      }
      // Otherwise, all the previous checks passed, but somebody else changed the state in the meantime. Loop again.
    }
  }
}
