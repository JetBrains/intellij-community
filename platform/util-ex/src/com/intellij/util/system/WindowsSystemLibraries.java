// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;

/** Looks up a system DLL by its absolute path under {@code %SystemRoot%\System32}, so the DLL search order cannot substitute it. */
final class WindowsSystemLibraries {
  private WindowsSystemLibraries() { }

  static @NotNull SymbolLookup lookup(@NotNull String dllName) {
    return SymbolLookup.libraryLookup(systemRoot().resolve("System32").resolve(dllName), Arena.global());
  }

  private static @NotNull Path systemRoot() {
    String systemRoot = System.getenv("SystemRoot");
    return Path.of(systemRoot != null ? systemRoot : "C:\\Windows");
  }
}
