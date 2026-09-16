// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Looks up a system DLL by its absolute path under {@code %SystemRoot%\System32}, so the DLL search order cannot substitute it. */
@ApiStatus.Internal
public final class WindowsSystemLibraries {
  private WindowsSystemLibraries() { }

  /** One lookup per library, because several classes ask for the same one. */
  private static final Map<String, SymbolLookup> ourLookups = new ConcurrentHashMap<>();

  public static @NotNull SymbolLookup lookup(@NotNull String dllName) {
    return ourLookups.computeIfAbsent(dllName, name -> SymbolLookup.libraryLookup(systemRoot().resolve("System32").resolve(name), Arena.global()));
  }

  private static @NotNull Path systemRoot() {
    var systemRoot = System.getenv("SystemRoot");
    return Path.of(systemRoot != null ? systemRoot : "C:\\Windows");
  }
}
