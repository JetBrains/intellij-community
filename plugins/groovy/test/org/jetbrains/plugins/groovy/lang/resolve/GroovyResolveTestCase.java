/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @uthor ven
 */
public abstract class GroovyResolveTestCase extends ResolveTestCase {

  protected abstract String getTestDataPath();

  protected void setUp() throws Exception {
    super.setUp();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    assertNotNull(root);
    rootModel.setJdk(JavaSdk.getInstance().createJdk("java sdk", TestUtils.getMockJdkHome(), false));
    final VirtualFile testRoot = root.findChild(getTestName(true));
    ContentEntry contentEntry = rootModel.addContentEntry(testRoot);
    assertNotNull(testRoot);
    contentEntry.addSourceFolder(testRoot, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });

    GroovyPsiManager.getInstance(getProject()).buildGDK();
  }
}
