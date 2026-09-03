// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** Known folder paths through {@code SHGetKnownFolderPath} downcalls into {@code shell32.dll}. Windows only: the first call loads the DLLs. */
@ApiStatus.Internal
public final class WindowsShell {
  private WindowsShell() { }

  /** {@code FOLDERID_Desktop} */
  public static final UUID FOLDERID_DESKTOP = UUID.fromString("B4BFCC3A-DB2C-424C-B029-7FE99A87C641");
  /** {@code FOLDERID_UserProgramFiles}, {@code %LOCALAPPDATA%\\Programs} */
  public static final UUID FOLDERID_USER_PROGRAM_FILES = UUID.fromString("5CD7AEE2-2219-4A67-B85D-6C9CE15660CB");
  /** {@code FOLDERID_LocalAppData} */
  public static final UUID FOLDERID_LOCAL_APP_DATA = UUID.fromString("F1B32785-6FBA-4FCF-9D55-7B8E7F157091");

  /** @return the path of the known folder for the current user, or {@code null} when the shell reports a failure */
  public static @Nullable String knownFolderPath(@NotNull UUID folderId) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment path = arena.allocate(ADDRESS);
      int hresult = (int)Handles.SH_GET_KNOWN_FOLDER_PATH.invokeExact(guid(arena, folderId), 0, MemorySegment.NULL, path);
      if (hresult != 0) {
        return null;
      }
      MemorySegment text = path.get(ADDRESS, 0);
      if (text.address() == 0) {
        return null;
      }
      try {
        return text.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE);
      }
      finally {
        Handles.CO_TASK_MEM_FREE.invokeExact(text);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** A {@code GUID} in memory: {@code Data1}, {@code Data2} and {@code Data3} in native order, then the eight {@code Data4} bytes as written. */
  private static MemorySegment guid(Arena arena, UUID id) {
    MemorySegment guid = arena.allocate(16);
    long high = id.getMostSignificantBits();
    guid.set(JAVA_INT, 0, (int)(high >>> 32));
    guid.set(JAVA_SHORT, 4, (short)(high >>> 16));
    guid.set(JAVA_SHORT, 6, (short)high);
    long low = id.getLeastSignificantBits();
    for (int i = 0; i < 8; i++) {
      guid.set(JAVA_BYTE, 8 + i, (byte)(low >>> (56 - 8 * i)));
    }
    return guid;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code HRESULT SHGetKnownFolderPath(REFKNOWNFOLDERID, DWORD flags, HANDLE token, PWSTR *path)} */
    static final MethodHandle SH_GET_KNOWN_FOLDER_PATH = LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("shell32.dll").findOrThrow("SHGetKnownFolderPath"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    /** {@code void CoTaskMemFree(LPVOID)} */
    static final MethodHandle CO_TASK_MEM_FREE = LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("ole32.dll").findOrThrow("CoTaskMemFree"), FunctionDescriptor.ofVoid(ADDRESS));
  }
}
