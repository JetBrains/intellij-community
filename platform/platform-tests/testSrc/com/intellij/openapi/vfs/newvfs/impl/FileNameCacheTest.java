// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.SLRUFileNameCache;
import com.intellij.util.io.InMemoryEnumerator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;

public class FileNameCacheTest {

  private FileNameCache cache;

  @BeforeEach
  public void setUp() throws Exception {
    cache = new SLRUFileNameCache(new InMemoryEnumerator<>());
  }

  @AfterEach
  public void disposeVFS() throws Exception {
  }

  @Test
  public void testAssertShortFileNameWithWindowsUNC() throws IOException {
    final boolean isValidName = SystemInfo.isWindows;
    checkFileName("//wsl$/Ubuntu", isValidName); // valid for Windows, invalid in other case
    checkFileName("//wsl$//Ubuntu", false);
  }

  private void checkFileName(@NotNull String name, boolean isValid) throws IOException {
    if (isValid) {
      cache.enumerate(name);
    }
    else {
      try {
        cache.enumerate(name);
        fail();
      }
      catch (Exception expected) {
      }
    }
  }
}
