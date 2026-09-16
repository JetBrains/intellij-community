// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up a system DLL by its absolute path under {@code %SystemRoot%\System32}, so the DLL search order cannot
 * substitute it.
 * <p>
 * A library that more than one class needs has a method of its own below, so its name appears once. Use
 * {@link #lookup} for a library that one class alone needs.
 */
@ApiStatus.Internal
public final class WindowsSystemLibraries {
  private WindowsSystemLibraries() { }

  /** One lookup per library, because several classes ask for the same one. */
  private static final Map<String, SymbolLookup> ourLookups = new ConcurrentHashMap<>();

  /** {@code kernel32.dll}: the process, file and handle calls. */
  public static @NotNull SymbolLookup kernel32() {
    return lookup("kernel32.dll");
  }

  /** {@code ntdll.dll}: the NT calls below the Win32 layer. */
  public static @NotNull SymbolLookup ntdll() {
    return lookup("ntdll.dll");
  }

  /** {@code ole32.dll}: the COM apartment and the COM allocator. */
  public static @NotNull SymbolLookup ole32() {
    return lookup("ole32.dll");
  }

  /** {@code shell32.dll}: the shell folders and the shell actions. */
  public static @NotNull SymbolLookup shell32() {
    return lookup("shell32.dll");
  }

  /** {@code advapi32.dll}: the registry, the access tokens and the services. */
  public static @NotNull SymbolLookup advapi32() {
    return lookup("advapi32.dll");
  }

  /** {@code psapi.dll}: the process status calls. */
  public static @NotNull SymbolLookup psapi() {
    return lookup("psapi.dll");
  }

  /** {@code user32.dll}: the windows, the icons and the system metrics. */
  public static @NotNull SymbolLookup user32() {
    return lookup("user32.dll");
  }

  /** {@code gdi32.dll}: the drawing calls. */
  public static @NotNull SymbolLookup gdi32() {
    return lookup("gdi32.dll");
  }

  public static @NotNull SymbolLookup lookup(@NotNull String dllName) {
    return ourLookups.computeIfAbsent(dllName, name -> SymbolLookup.libraryLookup(systemRoot().resolve("System32").resolve(name), Arena.global()));
  }

  private static @NotNull Path systemRoot() {
    var systemRoot = System.getenv("SystemRoot");
    return Path.of(systemRoot != null ? systemRoot : "C:\\Windows");
  }
}
