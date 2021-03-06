// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.loader;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

public final class NativeLibraryLoader {
  public static void loadPlatformLibrary(@NotNull String libName) {
    String baseName = libName;
    if (CpuArch.isIntel64()) {
      baseName = baseName.replace("32", "") + "64";
    }

    String libFileName = System.mapLibraryName(baseName).replace(".jnilib", ".dylib");
    Path libFile = PathManager.findBinFile(libFileName);
    if (libFile == null) {
      File libDir = new File(PathManager.getBinPath());
      throw new UnsatisfiedLinkError("'" + libFileName + "' not found in '" + libDir + "' among " + Arrays.toString(libDir.list()));
    }

    System.load(libFile.toString());
  }
}
