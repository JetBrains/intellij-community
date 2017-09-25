/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.config.EclipseClasspathConverter;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class EclipseEmlTest extends IdeaTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", "eml");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getProject().getBaseDir());
  }


  protected static void doTest(String relativePath, final Project project) throws Exception {
    final String path = project.getBaseDir().getPath() + relativePath;
    final Module module = doLoadModule(path, project);


    checkModule(path, module);
  }

  private static Module doLoadModule(@NotNull String path, @NotNull Project project) throws IOException, JDOMException, InvalidDataException {
    Module module = WriteAction.compute(
      () -> ModuleManager.getInstance(project).newModule(path + '/' + EclipseProjectFinder.findProjectName(path) + ModuleManagerImpl.IML_EXTENSION, StdModuleTypes.JAVA.getId()));

    replaceRoot(path, EclipseXml.DOT_CLASSPATH_EXT, project);

    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    new EclipseClasspathConverter(module).readClasspath(rootModel);
    WriteAction.run(() -> rootModel.commit());
    return module;
  }

  protected static void checkModule(String path, Module module) throws WriteExternalException {
    ModuleRootModel rootModel = ModuleRootManager.getInstance(module);
    final Element root = new Element("component");
    IdeaSpecificSettings.writeIdeaSpecificClasspath(root, rootModel);

    assertThat(root).isEqualTo(Paths.get(path, module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX));
  }

  private static void replaceRoot(String path, final String child, final Project project) throws IOException, JDOMException {
    final File emlFile = new File(path, child);
    String fileText = FileUtil.loadFile(emlFile).replaceAll("\\$ROOT\\$", project.getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }
    JDOMUtil.write(JDOMUtil.load(fileText), emlFile, "\n");
  }

  public void testSrcInZip() throws Exception {
    doTest("/test", getProject());
  }

  public void testPreserveInheritedInvalidJdk() throws Exception {
    final Project project = getProject();
    final String projectBasePath = project.getBaseDir().getPath();
    final String path = projectBasePath + "/test";

    final Module module = doLoadModule(path, project);

    ModuleRootModificationUtil.setSdkInherited(module);

    checkModule(projectBasePath + "/test/expected", module);
  }
}
