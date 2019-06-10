// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.loader;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

public class NativeLibraryLoader {
  public static void loadPlatformLibrary(@NotNull String libName) {
    String libFileName = mapLibraryName(libName);

    final String libPath;
    final File libFile = PathManager.findBinFile(libFileName);

    if (libFile != null) {
      libPath = libFile.getAbsolutePath();
    }
    else {
      if (!new File(libPath = PathManager.getHomePathFor(IdeaWin32.class) + "/bin/" + libFileName).exists()) {
        File libDir = new File(PathManager.getBinPath());
        throw new UnsatisfiedLinkError("'" + libFileName + "' not found in '" + libDir + "' among " + Arrays.toString(libDir.list()));
      }
    }

    System.load(libPath);
  }

  @NotNull
  private static String mapLibraryName(@NotNull String libName) {
    String baseName = libName;
    if (SystemInfo.is64Bit) {
      baseName = baseName.replace("32", "") + "64";
    }
    String fileName = System.mapLibraryName(baseName);
    if (SystemInfo.isMac) {
      fileName = fileName.replace(".jnilib", ".dylib");
    }
    return fileName;
  }
}
