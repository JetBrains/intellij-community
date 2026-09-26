// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The POSIX user ids of the current process, read with {@code getuid} and {@code geteuid} downcalls.
 * Neither call has a failure mode. Unix only: the symbol is looked up on the first call.
 */
@ApiStatus.Internal
public final class PosixIds {
  private PosixIds() { }

  /** {@code uid_t getuid(void)}, the real user id. {@code uid_t} is a 32-bit integer on every supported target. */
  @LowLevelLocalMachineAccess
  public static int getuid() {
    try {
      return (int)Handles.GETUID.invokeExact();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code uid_t geteuid(void)}, the effective user id. */
  @LowLevelLocalMachineAccess
  public static int geteuid() {
    try {
      return (int)Handles.GETEUID.invokeExact();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    static final MethodHandle GETUID = downcall("getuid");
    static final MethodHandle GETEUID = downcall("geteuid");

    private static MethodHandle downcall(String name) {
      return LINKER.downcallHandle(LINKER.defaultLookup().findOrThrow(name), FunctionDescriptor.of(JAVA_INT));
    }
  }
}
