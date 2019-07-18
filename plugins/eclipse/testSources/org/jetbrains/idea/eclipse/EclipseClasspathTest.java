// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;

import java.io.File;
import java.io.IOException;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class EclipseClasspathTest extends JavaProjectTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", "round");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    final VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getProject().getBaseDir());
  }

  private void doTest() throws Exception {
    doTest("/test", getProject());
  }

  protected static void doTest(final String relativePath, final Project project) throws Exception {
    final String path = project.getBasePath() + relativePath;
    checkModule(path, setUpModule(path, project));
  }

  static Module setUpModule(final String path, @NotNull final Project project) throws Exception {
    final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
    String fileText = FileUtil.loadFile(classpathFile).replaceAll("\\$ROOT\\$", project.getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }
    final Element classpathElement = JDOMUtil.load(fileText);

    final Module module = WriteCommandAction.runWriteCommandAction(null, (Computable<Module>)() -> {
      String imlPath = path + "/" + EclipseProjectFinder.findProjectName(path) + ModuleManagerImpl.IML_EXTENSION;
      return ModuleManager.getInstance(project).newModule(imlPath, StdModuleTypes.JAVA.getId());
    });

    ModuleRootModificationUtil.updateModel(module, model -> {
      try {
        EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, project, null);
        classpathReader.init(model);
        classpathReader.readClasspath(model, classpathElement);
        new EclipseClasspathStorageProvider().assertCompatible(model);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    return module;
  }

  static void checkModule(String path, Module module) throws IOException, JDOMException, ConversionException {
    final File classpathFile1 = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
    if (!classpathFile1.exists()) return;
    String fileText1 = FileUtil.loadFile(classpathFile1).replaceAll("\\$ROOT\\$", module.getProject().getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText1 = fileText1.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }

    Element classpathElement1 = JDOMUtil.load(fileText1);
    ModuleRootModel model = ModuleRootManager.getInstance(module);
    Element resultClasspathElement = new EclipseClasspathWriter().writeClasspath(classpathElement1, model);
    assertThat(resultClasspathElement).isEqualTo(resultClasspathElement);
  }

  public void testAbsolutePaths() throws Exception {
    doTest("/parent/parent/test", getProject());
  }

  public void testWorkspaceOnly() throws Exception {
    doTest();
  }

  public void testExportedLibs() throws Exception {
    doTest();
  }

  public void testPathVariables() throws Exception {
    doTest();
  }

  public void testJunit() throws Exception {
    doTest();
  }

  public void testSrcBinJRE() throws Exception {
    doTest();
  }

  public void testSrcBinJRESpecific() throws Exception {
    doTest();
  }

  public void testNativeLibs() throws Exception {
    doTest();
  }

  public void testAccessrulez() throws Exception {
    doTest();
  }

  public void testSrcBinJREProject() throws Exception {
    doTest();
  }

  public void testSourceFolderOutput() throws Exception {
    doTest();
  }

  public void testMultipleSourceFolders() throws Exception {
    doTest();
  }

  public void testEmptySrc() throws Exception {
    doTest();
  }

  public void testHttpJavadoc() throws Exception {
    doTest();
  }

  public void testHome() throws Exception {
    doTest();
  }

  //public void testNoJava() throws Exception {
  //  doTest();
  //}

  public void testNoSource() throws Exception {
    doTest();
  }

  public void testPlugin() throws Exception {
    doTest();
  }

  public void testRoot() throws Exception {
    doTest();
  }

  public void testUnknownCon() throws Exception {
    doTest();
  }

  public void testSourcesAfterAll() throws Exception {
    doTest();
  }

  public void testLinkedSrc() throws Exception {
    doTest();
  }

  public void testSrcRootsOrder() throws Exception {
    doTest();
  }
}
