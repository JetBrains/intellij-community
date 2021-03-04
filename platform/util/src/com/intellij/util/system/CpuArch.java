// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.system;

/**
 * Represents a CPU architecture this Java VM is executed on.
 * May not correspond to the actual hardware if a JVM is "virtualized" (e.g. macOS/Intel binary under Rosetta 2).
 */
public enum CpuArch {
  X86, X86_64, ARM64, OTHER, UNKNOWN;

  public static final CpuArch CURRENT;
  static {
    String arch = System.getProperty("os.arch");
    if ("x86_64".equals(arch) || "amd64".equals(arch)) {
      CURRENT = X86_64;
    }
    else if ("i386".equals(arch) || "x86".equals(arch)) {
      CURRENT = X86;
    }
    else if ("aarch64".equals(arch) || "arm64".equals(arch)) {
      CURRENT = ARM64;
    }
    else if (arch == null || arch.isEmpty()) {
      CURRENT = UNKNOWN;
    }
    else {
      CURRENT = OTHER;
    }
  }
}
