// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * The POSIX user ids of the current process, read with {@code getuid} and {@code geteuid} downcalls.
 * <p>
 * Neither call has a failure mode. Call them on a Unix-like OS only: on Windows the symbol lookup fails
 * and the first call throws {@link IllegalStateException}.
 */
@ApiStatus.Internal
public final class PosixIds {
  private PosixIds() { }

  /** {@code uid_t getuid(void)}. {@code uid_t} is a 32-bit integer on every supported target. */
  @LowLevelLocalMachineAccess
  public static int getuid() {
    return call(Handles.GETUID);
  }

  /** {@code uid_t geteuid(void)}, the effective user id. */
  @LowLevelLocalMachineAccess
  public static int geteuid() {
    return call(Handles.GETEUID);
  }

  private static int call(MethodHandle handle) {
    try {
      return (int)handle.invokeExact();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final MethodHandle GETUID = downcall("getuid");
    private static final MethodHandle GETEUID = downcall("geteuid");

    private static MethodHandle downcall(String name) {
      Linker linker = Linker.nativeLinker();
      return linker.downcallHandle(linker.defaultLookup().findOrThrow(name), FunctionDescriptor.of(ValueLayout.JAVA_INT));
    }
  }
}
