// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.java.execution.AbstractTestFrameworkIntegrationTest;
import com.intellij.java.execution.BaseConfigurationTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.Arrays;
import java.util.List;

public class JUnitModulePathTest extends BaseConfigurationTestCase {

  public void testModuleInfoInProductionNonModularizedJunit() throws Exception {
    Module module = createEmptyModule();
    JpsMavenRepositoryLibraryDescriptor nonModularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0");
    JUnitConfiguration configuration = setupConfiguration(nonModularizedJupiterDescription, "modulePath/prod1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    ParamsGroup moduleOptions = JavaTestFrameworkRunnableState.getJigsawOptions(params4Tests);
    assertNotNull(moduleOptions);
    assertEquals("--patch-module m1=" + CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests().getPath() +
                          " --add-reads m1=ALL-UNNAMED" +
                          " --add-opens m1/p=ALL-UNNAMED" +
                          " --add-modules m1", moduleOptions.getParametersList().getParametersString());

    checkLibrariesOnPathList(module, params4Tests.getClassPath());

    //production module output is on the module path
    PathsList modulePath = params4Tests.getModulePath();
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePath.getPathList().contains(getCompilerOutputPath(module)));
  }

  public void testModuleInfoInProductionModularizedJUnit() throws Exception {
    doTestModuleInfoInProductionModularizedJUnit(
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-engine", "5.5.2"));
  }

  public void testModuleInfoInProductionModularizedJUnitNoEngineDependency() throws Exception {
    doTestModuleInfoInProductionModularizedJUnit(
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2"));
  }

  private void doTestModuleInfoInProductionModularizedJUnit(JpsMavenRepositoryLibraryDescriptor modularizedJupiterDescription)
    throws Exception {
    Module module = createEmptyModule();
    JUnitConfiguration configuration = setupConfiguration(modularizedJupiterDescription, "modulePath/prod1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    ParamsGroup moduleOptions = JavaTestFrameworkRunnableState.getJigsawOptions(params4Tests);
    assertNotNull(moduleOptions);
    assertEquals("--patch-module m1=" + CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests().getPath() +
                          " --add-reads m1=ALL-UNNAMED" +
                          " --add-opens m1/p=ALL-UNNAMED" +
                          " --add-modules m1", moduleOptions.getParametersList().getParametersString());

    checkLibrariesOnPathList(module, params4Tests.getClassPath());

    //production module output is on the module path
    PathsList modulePath = params4Tests.getModulePath();
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePath.getPathList().contains(getCompilerOutputPath(module)));

    //test output on the classpath
    assertFalse("module path: " + modulePath.getPathsString(),
                         modulePath.getPathList().contains(getCompilerOutputPath(module, true)));
  }

  private static void checkLibrariesOnPathList(Module module, PathsList classPath) {
    Arrays.stream(
      OrderEnumerator.orderEntries(module).withoutModuleSourceEntries()
        .withoutDepModules()
        .withoutSdk()
        .recursively().exportedOnly().classes().usingCache().getRoots())
      .map(f -> PathUtil.getLocalPath(JarFileSystem.getInstance().getVirtualFileForJar(f)))
      .forEach(path -> assertTrue("path " + path + " is located on the classpath: " + classPath.getPathsString(),
                                           classPath.getPathList().contains(path)));
  }

  public void testNonModularizedProject() throws Exception {
    Module module = createEmptyModule();
    JUnitConfiguration configuration = setupConfiguration(new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2"),
                                                          "modulePath/prod1", module);
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = model.getContentEntries()[0];
      entry.removeSourceFolder(entry.getSourceFolders(JavaSourceRootType.SOURCE).get(0));
    });
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    assertEmpty(params4Tests.getModulePath().getPathList());
    assertFalse(params4Tests.getVMParametersList().getParametersString().contains("--add-modules"));
  }

  public void testModuleInfoInTestNonModularizedJunit() throws Exception {
    Module module = createEmptyModule();
    JpsMavenRepositoryLibraryDescriptor nonModularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0");
    JUnitConfiguration configuration = setupConfiguration(nonModularizedJupiterDescription, "modulePath/test1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    ParamsGroup moduleOptions = JavaTestFrameworkRunnableState.getJigsawOptions(params4Tests);
    assertNotNull(moduleOptions);
    assertEquals("--add-modules m1", moduleOptions.getParametersList().getParametersString());

    PathsList modulePath = params4Tests.getModulePath();

    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePath.getPathList().stream().anyMatch(filePath -> filePath.contains("junit-jupiter-api")));
    //production module output is not on the module path
    assertFalse("module path: " + modulePath.getPathsString(),
                         modulePath.getPathList().contains(getCompilerOutputPath(module)));
    //test module output is on the module path
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePath.getPathList().contains(getCompilerOutputPath(module, true)));
    assertSize(2, modulePath.getPathList());

    //launcher should be put on the classpath
    assertTrue(params4Tests.getClassPath().getPathList().stream().anyMatch(filePath -> filePath.contains("launcher")));
  }

  public void testModuleInfoInTestModularizedJunit() throws Exception {
    Module module = createEmptyModule();
    JpsMavenRepositoryLibraryDescriptor nonModularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2");
    JUnitConfiguration configuration = setupConfiguration(nonModularizedJupiterDescription, "modulePath/test1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    ParamsGroup moduleOptions = JavaTestFrameworkRunnableState.getJigsawOptions(params4Tests);
    assertNotNull(moduleOptions);
    assertEquals("--add-modules m1" +
                          " --add-modules org.junit.platform.launcher", moduleOptions.getParametersList().getParametersString());

    PathsList classPath = params4Tests.getClassPath();
    assertContainsElements(classPath.getPathList(), PathUtil.getJarPathForClass(JUnitStarter.class));
    assertContainsElements(classPath.getPathList(), TestObject.getJUnit5RtFile().getPath());

    PathsList modulePath = params4Tests.getModulePath();
    checkLibrariesOnPathList(module, modulePath);
    //production module output is not on the module path
    List<String> modulePathList = modulePath.getPathList();
    assertFalse("module path: " + modulePath.getPathsString(),
                         modulePathList.contains(getCompilerOutputPath(module)));
    //test module output is on the module path
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePathList.contains(getCompilerOutputPath(module, true)));
    assertTrue(modulePathList.stream().anyMatch(filePath -> filePath.contains("launcher")));
  }
  
  public void testModuleInfoInTestModularizedJunitDependencyOnAnotherModule() throws Exception {
    Module module1 = createEmptyModule();
    setupModule("modulePath/prod1", module1);
    Module module = createEmptyModule();
    ModuleRootModificationUtil.updateModel(module, model -> {
      model.addModuleOrderEntry(module1);
    });
    JpsMavenRepositoryLibraryDescriptor nonModularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2");
    JUnitConfiguration configuration = setupConfiguration(nonModularizedJupiterDescription, "modulePath/test2", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    ParamsGroup moduleOptions = JavaTestFrameworkRunnableState.getJigsawOptions(params4Tests);
    assertNotNull(moduleOptions);
    assertEquals("--add-modules m2" +
                          " --add-modules org.junit.platform.launcher", moduleOptions.getParametersList().getParametersString());

    PathsList classPath = params4Tests.getClassPath();
    assertContainsElements(classPath.getPathList(), PathUtil.getJarPathForClass(JUnitStarter.class));
    assertContainsElements(classPath.getPathList(), TestObject.getJUnit5RtFile().getPath());

    PathsList modulePath = params4Tests.getModulePath();
    checkLibrariesOnPathList(module, modulePath);
    List<String> modulePathList = modulePath.getPathList();
    assertFalse("module path: " + modulePath.getPathsString(),
                         modulePathList.contains(getCompilerOutputPath(module)));
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePathList.contains(getCompilerOutputPath(module1)));
    //test module output is on the module path
    assertTrue("module path: " + modulePath.getPathsString(),
                        modulePathList.contains(getCompilerOutputPath(module, true)));
    assertTrue(modulePathList.stream().anyMatch(filePath -> filePath.contains("launcher")));
  }

  private JUnitConfiguration setupConfiguration(JpsMavenRepositoryLibraryDescriptor libraryDescriptor, String sources, Module module) throws Exception {
    setupModule(sources, module);
    AbstractTestFrameworkIntegrationTest.addMavenLibs(module, libraryDescriptor);

    Sdk mockJdk = IdeaTestUtil.getMockJdk9();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(mockJdk, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(module, mockJdk);

    PsiClass aClass = findClass(module, "p.MyTest");
    assertNotNull(aClass);
    assertNotNull(TestFrameworks.detectFramework(aClass));

    return createConfiguration(aClass);
  }

  private void setupModule(String sources, Module module) {
    VirtualFile contentRoot = findFile(sources);
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry contentEntry = model.addContentEntry(contentRoot);
      contentEntry.addSourceFolder(contentRoot.getUrl() + "/src", false);
      contentEntry.addSourceFolder(contentRoot.getUrl() + "/test", true);

      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPath(contentRoot.findFileByRelativePath("out/production"));
      moduleExtension.setCompilerOutputPathForTests(contentRoot.findFileByRelativePath("out/test"));
    });
  }

  private static String getCompilerOutputPath(Module module) {
    return getCompilerOutputPath(module, false);
  }

  private static String getCompilerOutputPath(Module module, boolean forTests) {
    CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
    return PathUtil.getLocalPath(forTests ? moduleExtension.getCompilerOutputPathForTests()
                                          : moduleExtension.getCompilerOutputPath());
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/junit/java-tests/testData";
  }
}
