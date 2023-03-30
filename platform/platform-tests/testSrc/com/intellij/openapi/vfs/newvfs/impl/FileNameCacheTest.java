// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FileNameCache;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class FileNameCacheTest extends HeavyPlatformTestCase {

  public void testAssertShortFileNameWithWindowsUNC() {
    final boolean isValidName = SystemInfo.isWindows;
    checkFileName("//wsl$/Ubuntu", isValidName); // valid for Windows, invalid in other case
    checkFileName("//wsl$//Ubuntu", false);
  }

  private static void checkFileName(@NotNull String name, boolean isValid) {
    if (isValid) {
      FileNameCache.storeName(name);
    }
    else {
      try {
        FileNameCache.storeName(name);
        fail();
      }
      catch (Exception expected) {}
    }
  }
}
