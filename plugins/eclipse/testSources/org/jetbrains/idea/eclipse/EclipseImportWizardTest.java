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
package org.jetbrains.idea.eclipse;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.ide.util.projectWizard.ImportFromSourcesProvider;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.eclipse.importWizard.EclipseImportBuilder;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectImportProvider;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class EclipseImportWizardTest extends ProjectWizardTestCase {

  public void testImportProject() throws Exception {
    Module module = doTest(".project");
    assertEquals("root", module.getName());
  }

  public void testImportClasspath() throws Exception {
    Module module = doTest(".classpath");
    assertEquals("root", module.getName());
  }

  public void testImportFromDirectory() throws Exception {
    Module module = doTest("");
    assertEquals("root", module.getName());
  }

  public void testDontImportOpenProject() throws Exception {
    copyTestData("");
    Module module = importProjectFrom(getProject().getBaseDir().getPath(), null,
                                      new EclipseProjectImportProvider(new EclipseImportBuilder()));
    assertNull(module);
  }

  private Module doTest(String filePath) throws IOException {
    copyTestData("subdir");
    return importProjectFrom(getProject().getBaseDir().getPath() + "/subdir/" + filePath, null,
                             new EclipseProjectImportProvider(new EclipseImportBuilder()));
  }

  private void copyTestData(String subdirName) throws IOException {
    final File currentTestRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", "import");
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    assertNotNull(vTestRoot);
    VirtualFile subdir = StringUtil.isEmpty(subdirName) ? getProject().getBaseDir() :
                         WriteAction.compute(() -> VfsUtil.createDirectoryIfMissing(getProject().getBaseDir(), subdirName));
    copyDirContentsTo(vTestRoot, subdir);
  }

  public void testImportingFromTwoProviders() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "Foo.java");
    FileUtil.writeToFile(file, "class Foo {}");
    Module module = importProjectFrom(file.getParent(), null, new ImportFromSourcesProvider(),
                                      new EclipseProjectImportProvider(new EclipseImportBuilder()));
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    assertEquals(1, sourceRoots.length);
    assertEquals(LocalFileSystem.getInstance().findFileByIoFile(file.getParentFile()), sourceRoots[0]);
  }
}
