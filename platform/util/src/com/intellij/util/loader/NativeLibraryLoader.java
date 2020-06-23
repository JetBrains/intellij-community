// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.loader;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class NativeLibraryLoader {
  public static void loadPlatformLibrary(@NotNull String libName) {
    String libFileName = mapLibraryName(libName);

    String libPath;
    Path libFile = PathManager.findBinFile(libFileName);

    if (libFile != null) {
      libPath = libFile.toAbsolutePath().toString();
    }
    else {
      libPath = PathManager.getHomePathFor(IdeaWin32.class) + "/bin/" + libFileName;
      if (!Files.exists(Paths.get(libPath))) {
        File libDir = new File(PathManager.getBinPath());
        throw new UnsatisfiedLinkError("'" + libFileName + "' not found in '" + libDir + "' among " + Arrays.toString(libDir.list()));
      }
    }

    System.load(libPath);
  }

  private static @NotNull String mapLibraryName(@NotNull String libName) {
    String baseName = libName;
    if (SystemInfoRt.is64Bit) {
      baseName = baseName.replace("32", "") + "64";
    }
    String fileName = System.mapLibraryName(baseName);
    if (SystemInfoRt.isMac) {
      fileName = fileName.replace(".jnilib", ".dylib");
    }
    return fileName;
  }
}
