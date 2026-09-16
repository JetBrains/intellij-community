// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;
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
import static java.lang.foreign.ValueLayout.JAVA_INT;

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
  public static final UUID FOLDERID_DOWNLOADS = UUID.fromString("374DE290-123F-4565-9164-39C4925E467B");

  /** @return the path of the known folder for the current user, or {@code null} when the shell reports a failure */
  public static @Nullable String knownFolderPath(@NotNull UUID folderId) {
    try (var arena = Arena.ofConfined()) {
      var path = arena.allocate(ADDRESS);
      var hresult = (int)Handles.SH_GET_KNOWN_FOLDER_PATH.invokeExact(WindowsCom.guid(arena, folderId), 0, MemorySegment.NULL, path);
      var text = path.get(ADDRESS, 0);
      if (text.address() == 0) {
        return null;
      }
      try {
        return hresult == 0 ? text.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE) : null;
      }
      finally {
        Handles.CO_TASK_MEM_FREE.invokeExact(text);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
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
