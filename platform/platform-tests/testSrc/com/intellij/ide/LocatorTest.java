// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LocatorTest extends TestCase {
  public void test() throws IOException {
    File locatorFile = new File(PathManager.getSystemPath() + "/" + ApplicationEx.LOCATOR_FILE_NAME);
    assertTrue("doesn't exist: " + locatorFile.getPath(), locatorFile.exists());
    assertTrue("can't read: " + locatorFile.getPath(), locatorFile.canRead());

    String home = FileUtil.loadFile(locatorFile, StandardCharsets.UTF_8);
    assertTrue(home, StringUtil.isNotEmpty(home));

    assertEquals(home, PathManager.getHomePath());
  }
}