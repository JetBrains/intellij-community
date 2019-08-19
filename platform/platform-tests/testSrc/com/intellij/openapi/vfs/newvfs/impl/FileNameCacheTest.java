// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import org.junit.Test;

import static org.junit.Assert.fail;

public class FileNameCacheTest {
  @Test
  public void testAssertShortFileNameWithWindowsUNC() {
    FileNameCache.assertShortFileName("//wsl$/Ubuntu", true);

    try {
      FileNameCache.assertShortFileName("//wsl$//Ubuntu", true);
      fail();
    }
    catch (Exception expected) {
    }
  }
}
