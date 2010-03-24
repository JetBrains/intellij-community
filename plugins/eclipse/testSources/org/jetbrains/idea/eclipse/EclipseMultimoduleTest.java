/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import junit.framework.Assert;

import java.io.File;
import java.io.IOException;

public class EclipseMultimoduleTest extends IdeaTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", "round");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    try {
      FileUtil.copyDir(currentTestRoot, new File(getProject().getBaseDir().getPath()));
    }
    catch (IOException e) {
      LOG.error(e);
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    final VirtualFile file =
      ApplicationManager.getApplication().runWriteAction(
          new Computable<VirtualFile>() {
            public VirtualFile compute() {
              return LocalFileSystem.getInstance().refreshAndFindFileByPath(getProject().getBaseDir().getPath() + "/eclipse-ws-3.4.1-a/ws-internals");
            }
          }
      );
    if (file != null) {
      model.addContentEntry(file);
    } else {
      model.dispose();
      Assert.assertTrue("File not found", false);
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run(){
            model.commit();
        }
    });
  }

  @Override
  protected Module createMainModule() throws IOException {
    return createModule("ws-internals");
  }

  public void testAllProps() throws Exception {
    EclipseClasspathTest.doTest("/eclipse-ws-3.4.1-a/all-props", getProject());
  }


}