// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.ModuleManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Paths;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class EclipseImlTest extends JavaProjectTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData/iml");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getOrCreateProjectBaseDir());
  }

  private void doTest() throws Exception {
    doTest("/test", getProject());
  }

  private static Element findComponent(Element moduleRoot, String componentName) {
    for (Element component : moduleRoot.getChildren("component")) {
      if (componentName.equals(component.getAttributeValue("name"))) {
        return component;
      }
    }

    throw new IllegalStateException("Could not find component '" + componentName + "' in module xml: " + JDOMUtil.writeElement(moduleRoot));
  }

  protected static void doTest(final String relativePath, final Project project) throws Exception {
    final String path = project.getBasePath() + relativePath;

    final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
    String fileText = FileUtil.loadFile(classpathFile).replaceAll("\\$ROOT\\$", project.getBasePath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }

    String moduleImlPath = new File(path) + File.separator + EclipseProjectFinder
      .findProjectName(path) + ModuleManagerEx.IML_EXTENSION;

    final Element classpathElement = JDOMUtil.load(fileText);
    final Module module = WriteCommandAction.runWriteCommandAction(null, (Computable<Module>)() -> ModuleManager.getInstance(project)
      .newModule(moduleImlPath, StdModuleTypes.JAVA.getId()));
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, project, null);
    classpathReader.init(rootModel);
    classpathReader.readClasspath(rootModel, classpathElement);
    ApplicationManager.getApplication().runWriteAction(rootModel::commit);

    StoreUtil.saveDocumentsAndProjectSettings(project);

    String junit3Path = ContainerUtil.getFirstItem(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit3"));
    String junit4Path = ContainerUtil.find(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4"),
                                           jarPath -> PathUtil.getFileName(jarPath).startsWith("junit"));
    ReplacePathToMacroMap macroMap = new ReplacePathToMacroMap(PathMacroManager.getInstance(module).getReplacePathMap());
    macroMap.addMacroReplacement(junit3Path, "JUNIT3_PATH");
    macroMap.addMacroReplacement(junit4Path, "JUNIT4_PATH");
    macroMap.addMacroReplacement(Paths.get(junit3Path).toRealPath().toString(), "JUNIT3_PATH");
    macroMap.addMacroReplacement(Paths.get(junit4Path).toRealPath().toString(), "JUNIT4_PATH");

    final Element moduleElement = JDOMUtil.load(new File(moduleImlPath));
    PathMacroManager.getInstance(module).getExpandMacroMap().substitute(moduleElement, true);

    final Element actualImlElement = findComponent(moduleElement, "NewModuleRootManager");
    actualImlElement.setName("root");
    actualImlElement.removeAttribute("name");

    StringWriter writer = new StringWriter();
    JbXmlOutputter xmlWriter = new JbXmlOutputter("\n", null, macroMap, null);
    xmlWriter.output(actualImlElement, writer);
    String actual = writer.toString();
    if (actual.contains("jar://$MAVEN_REPOSITORY$/junit") || actual.contains(".m2/repository/junit")) {
      fail(actual + "\n\n" + macroMap.toString());
    }
    assertThat(actual).toMatchSnapshot(Paths.get(project.getBasePath(), "expected", "expected.iml"));
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
