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

/*
 * User: anna
 * Date: 29-Mar-2010
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public abstract class EclipseVarsTest extends IdeaTestCase {
  @NonNls private static final String VARIABLE = "variable";
  @NonNls private static final String SRCVARIABLE = "srcvariable";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", getRelativeTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    final String tempPath = getProject().getBaseDir().getPath();
    final File tempDir = new File(tempPath);
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getProject().getBaseDir());

    final VirtualFile virtualTestDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(tempDir.getAbsolutePath(), virtualTestDir);
    virtualTestDir.refresh(false, true);

    PathMacros.getInstance().setMacro(VARIABLE, new File(tempPath, VARIABLE + "idea").getPath());
    PathMacros.getInstance().setMacro(SRCVARIABLE, new File(tempPath, SRCVARIABLE + "idea").getPath());
  }

  protected abstract String getRelativeTestPath();

  @Override
  protected void tearDown() throws Exception {
    PathMacros.getInstance().removeMacro(VARIABLE);
    PathMacros.getInstance().removeMacro(SRCVARIABLE);
    super.tearDown();
  }
}