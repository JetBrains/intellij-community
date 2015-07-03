/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.readWriteLock.ReadWriteAccessInspection;

import java.nio.charset.Charset;
import java.util.Collections;

public class ReadWriteLockInspectionTest extends LightCodeInsightFixtureTestCase {

  private final ReadWriteAccessInspection myInspection = new ReadWriteAccessInspection();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(
      FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/platform/core-api/src/com/intellij/util/readWriteLock");
    if (root == null) {
      return;
    }

    LocalFileSystem.getInstance().refreshFiles(Collections.singleton(root));
    for (VirtualFile file : root.getChildren()) {
      myFixture.addClass(new String(file.contentsToByteArray(), Charset.defaultCharset()));
    }
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/readWriteLock/";
  }

  private void doTest() {
    myFixture.enableInspections(myInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testLocalVariableAssignment() {
    doTest();
  }

  public void testCallsInMethod() {
    doTest();
  }
}
