// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** The AC line state from a {@code GetSystemPowerStatus} downcall into {@code kernel32.dll}. Windows only: the first call loads the DLL. */
@ApiStatus.Internal
public final class WindowsPower {
  private WindowsPower() { }

  /**
   * @return {@code SYSTEM_POWER_STATUS.ACLineStatus}: 0 offline, 1 online, 255 unknown
   * @throws IOException with the {@code GetLastError} code when the call fails
   */
  public static int acLineStatus() throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      MemorySegment status = arena.allocate(Handles.SYSTEM_POWER_STATUS);
      int succeeded = (int)Handles.GET_SYSTEM_POWER_STATUS.invokeExact(callState, status);
      if (succeeded == 0) {
        throw new IOException("GetSystemPowerStatus(): " + (int)Handles.LAST_ERROR.get(callState, 0L));
      }
      return Byte.toUnsignedInt(status.get(JAVA_BYTE, 0));
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code SYSTEM_POWER_STATUS { BYTE ACLineStatus, BatteryFlag, BatteryLifePercent, SystemStatusFlag; DWORD BatteryLifeTime, BatteryFullLifeTime; }}, 12 bytes */
    static final StructLayout SYSTEM_POWER_STATUS = MemoryLayout.structLayout(
      JAVA_BYTE.withName("ACLineStatus"), JAVA_BYTE.withName("BatteryFlag"), JAVA_BYTE.withName("BatteryLifePercent"), JAVA_BYTE.withName("SystemStatusFlag"),
      JAVA_INT.withName("BatteryLifeTime"), JAVA_INT.withName("BatteryFullLifeTime"));

    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    /** {@code BOOL GetSystemPowerStatus(LPSYSTEM_POWER_STATUS)}, with {@code GetLastError} captured into the leading call-state argument */
    static final MethodHandle GET_SYSTEM_POWER_STATUS = LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("GetSystemPowerStatus"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS),
      Linker.Option.captureCallState("GetLastError"));
  }
}
