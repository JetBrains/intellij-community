// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Signal delivery through a libc {@code kill} downcall. Unix only: the symbol is looked up on the first call.
 */
@ApiStatus.Internal
public final class PosixSignals {
  private PosixSignals() { }

  /**
   * Sends {@code signal} to the process {@code pid}, to the process group {@code -pid}, or to every process of the caller when {@code pid} is -1.
   *
   * @return the {@code kill(2)} result: 0 on success, -1 on failure
   */
  public static int kill(int pid, int signal) {
    try {
      return (int)Handles.KILL.invokeExact(pid, signal);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code int kill(pid_t pid, int sig)}; {@code pid_t} is {@code int} on Linux, macOS and FreeBSD */
    static final MethodHandle KILL = LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("kill"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
  }
}
