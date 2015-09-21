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
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;

import java.io.File;

public class EclipseImlTest extends IdeaTestCase {
  @NonNls private static final String JUNIT = "JUNIT";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData/iml");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getProject().getBaseDir());
  }

  private void doTest() throws Exception {
    doTest("/test", getProject());
  }

  protected static void doTest(final String relativePath, final Project project) throws Exception {
    final String path = project.getBaseDir().getPath() + relativePath;

    final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
    String fileText = FileUtil.loadFile(classpathFile).replaceAll("\\$ROOT\\$", project.getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }
    String communityLib = FileUtil.toSystemIndependentName(PathManagerEx.findFileUnderCommunityHome("lib").getAbsolutePath());
    fileText = fileText.replaceAll("\\$" + JUNIT + "\\$", communityLib);
    final Element classpathElement = JDOMUtil.loadDocument(fileText).getRootElement();
    final Module module = WriteCommandAction.runWriteCommandAction(null, new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(project)
          .newModule(new File(path) + File.separator + EclipseProjectFinder
            .findProjectName(path) + IdeaXml.IML_EXT, StdModuleTypes.JAVA.getId());
      }
    });
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, project, null);
    classpathReader.init(rootModel);
    classpathReader.readClasspath(rootModel, classpathElement);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });

    final Element actualImlElement = new Element("root");
    ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).getState().writeExternal(actualImlElement);

    PathMacros.getInstance().setMacro(JUNIT, communityLib);
    PathMacroManager.getInstance(module).collapsePaths(actualImlElement);
    PathMacroManager.getInstance(project).collapsePaths(actualImlElement);
    PathMacros.getInstance().removeMacro(JUNIT);

    final Element expectedIml =
      JDOMUtil.loadDocument(new File(project.getBaseDir().getPath() + "/expected", "expected.iml")).getRootElement();
    PlatformTestUtil.assertElementsEqual(expectedIml, actualImlElement);
  }


  public void testWorkspaceOnly() throws Exception {
    doTest();
  }

  public void testSrcBinJREProject() throws Exception {
    doTest();
  }


  public void testEmptySrc() throws Exception {
    doTest();
  }

  public void testEmpty() throws Exception {
    doTest();
  }

  public void testRoot() throws Exception {
    doTest();
  }
}
