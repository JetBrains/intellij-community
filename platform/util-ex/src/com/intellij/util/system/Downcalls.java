// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;

/**
 * Runs a body that makes a downcall.
 * <p>
 * {@code MethodHandle.invokeExact} declares {@code throws Throwable}, so every caller needs a catch. A downcall
 * reports a real failure through its answer, not through a throwable, so a throwable here is a defect of the binding:
 * a wrong descriptor, a missing symbol or a bad argument type. Each method below turns it into an
 * {@link IllegalStateException}.
 * <p>
 * A method exists per return kind, so an {@code int} or a {@code long} answer needs no box.
 */
final class Downcalls {
  private Downcalls() { }

  static int callInt(@NotNull IntBody body) {
    try {
      return body.run();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  static @NotNull MemorySegment callSegment(@NotNull SegmentBody body) {
    try {
      return body.run();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  static <T> @Nullable T call(@NotNull Body<T> body) {
    try {
      return body.run();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Write {@code body} as a block, for example {@code () -> { handle.invokeExact(); }}. In an expression body,
   * {@code invokeExact} gets the call type {@code ()Object}, and a {@code void} handle throws at the call.
   */
  static void run(@NotNull VoidBody body) {
    try {
      body.run();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @FunctionalInterface
  interface IntBody {
    int run() throws Throwable;
  }

  @FunctionalInterface
  interface SegmentBody {
    @NotNull MemorySegment run() throws Throwable;
  }

  @FunctionalInterface
  interface Body<T> {
    @Nullable T run() throws Throwable;
  }

  @FunctionalInterface
  interface VoidBody {
    void run() throws Throwable;
  }
}
