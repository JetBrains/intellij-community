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
 * Date: 26-Mar-2010
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class Eclipse2ModulesTest extends IdeaTestCase {
  @NonNls
  protected static final String DEPEND_MODULE_NAME = "ws-internals";
  private String myDependantModulePath = "ws-internals";

  protected abstract String getTestPath();

  @Override
  protected void setUpModule() {
    super.setUpModule();

    File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", getTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    VirtualFile baseDir = getProject().getBaseDir();
    assert baseDir != null;

    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getProject().getBaseDir());
  }

  @Override
  protected Module createMainModule() {
    return createModule(DEPEND_MODULE_NAME);
  }

  protected void doTest(@NotNull String workspaceRoot, @NotNull String projectRoot) throws Exception {
    VirtualFile baseDir = getProject().getBaseDir();
    assert baseDir != null;
    final String path = baseDir.getPath() + "/" + workspaceRoot + "/" + myDependantModulePath;
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file == null) {
      throw new AssertionError("File " + path + " not found");
    }

    PsiTestUtil.addContentRoot(getModule(), file);
  }

  public void setDependantModulePath(String dependantModulePath) {
    myDependantModulePath = dependantModulePath;
  }
}