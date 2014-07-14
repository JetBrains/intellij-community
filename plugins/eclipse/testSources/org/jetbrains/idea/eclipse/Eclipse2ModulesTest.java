/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public abstract class Eclipse2ModulesTest extends IdeaTestCase {
  @NonNls
  protected static final String DEPEND_MODULE_NAME = "ws-internals";
  private String myDependantModulePath = "ws-internals";

  protected abstract String getTestPath();

  @Override
  protected void setUpModule() {
    super.setUpModule();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", getTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    try {
      final VirtualFile baseDir = getProject().getBaseDir();
      assert baseDir != null;
      FileUtil.copyDir(currentTestRoot, new File(baseDir.getPath()));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  protected Module createMainModule() throws IOException {
    return createModule(DEPEND_MODULE_NAME);
  }

  protected void doTest(final String workspaceRoot, final String projectRoot) throws Exception {
    final VirtualFile file =
      WriteCommandAction.runWriteCommandAction(null, new Computable<VirtualFile>() {
        @Override
        @Nullable
        public VirtualFile compute() {
          final VirtualFile baseDir = getProject().getBaseDir();
          assert baseDir != null;
          return LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(baseDir.getPath() + "/" + workspaceRoot + "/" + myDependantModulePath);
        }
      });
    if (file != null) {
      PsiTestUtil.addContentRoot(getModule(), file);
    }
    else {
      Assert.assertTrue("File not found", false);
    }
  }

  public void setDependantModulePath(String dependantModulePath) {
    myDependantModulePath = dependantModulePath;
  }
}