/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.IOException;

public class LocatorTest extends PlatformTestCase {
  public void test() throws IOException {
    File locatorFile = new File(PathManager.getSystemPath() + "/" + ApplicationEx.LOCATOR_FILE_NAME);
    assertTrue("doesn't exist: " + locatorFile.getPath(), locatorFile.exists());
    assertTrue("can't read: " + locatorFile.getPath(), locatorFile.canRead());

    String home = FileUtil.loadFile(locatorFile, CharsetToolkit.UTF8_CHARSET);
    assertTrue(home, StringUtil.isNotEmpty(home));

    assertEquals(home, PathManager.getHomePath());
  }
}