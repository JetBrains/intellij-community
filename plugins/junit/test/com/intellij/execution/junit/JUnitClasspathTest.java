// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JUnitClasspathTest extends JavaCodeInsightFixtureTestCase {
  public void testWorkingDirsFileWhenConfigurationSpansToMultipleModules() throws Exception {
    final Module mod1 = setupModule("mod1", "T1");
    final Module mod2 = setupModule("mod2", "T2");
    CompilerTester compiler = createCompilerTester();
    compiler.rebuild();

    final JUnitConfiguration configuration = new JUnitConfiguration("p", getProject());
    configuration.setWorkingDirectory("$MODULE_DIR$");
    final JUnitConfiguration.Data persistentData = configuration.getPersistentData();
    persistentData.setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(mod1);
    persistentData.PACKAGE_NAME = "p";
    persistentData.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;

    final ExecutionEnvironment environment =
      ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build();
    final TestPackage aPackage = new TestPackage(configuration, environment) {
      @Override
      protected boolean createTempFiles() {
        return true;
      }
    };

    //ensure no fork if single module is selected
    aPackage.createSearchingForTestsTask().startSearch();
    File workingDirsFile = aPackage.getWorkingDirsFile();
    assertNotNull(workingDirsFile);
    assertEmpty(FileUtil.loadFile(workingDirsFile));

    //ensure fork when whole project is used
    persistentData.setScope(TestSearchScope.WHOLE_PROJECT);
    aPackage.createSearchingForTestsTask().startSearch();
    workingDirsFile = aPackage.getWorkingDirsFile();
    assertNotNull(workingDirsFile);
    String file;
    aPackage.createSearchingForTestsTask().startSearch();
    workingDirsFile = aPackage.getWorkingDirsFile();
    assertNotNull(workingDirsFile);
    file = preparePathsForComparison(FileUtil.loadFile(workingDirsFile), mod1, mod2);
    assertEquals("p\n" + //package name
                 "MODULE_1\n" +   //working dir
                 "mod1\n" +       //module name
                 "CLASSPATH\n" +
                 "1\n" +          //number of classes
                 "p.T1\n" +       //list of classes 
                 "\n" +           //empty filters
                 //second module
                 "MODULE_2\n" +   //working dir
                 "mod2\n" +       //module name
                 "CLASSPATH\n" +
                 "1\n" +          //number of classes
                 "p.T2",          //class names 
                 file);
  }

  @NotNull
  private CompilerTester createCompilerTester() throws Exception {
    return new CompilerTester(myFixture, Arrays.asList(ModuleManager.getInstance(getProject()).getModules()));
  }

  public void testNoWorkingDirsFileWhenOnlyOneModuleExist() throws Exception {
    setupModule("mod1", "T1");
    CompilerTester compiler = createCompilerTester();
    compiler.rebuild();
    final JUnitConfiguration configuration = new JUnitConfiguration("p", getProject());
    configuration.setWorkingDirectory("$MODULE_DIR$");
    final JUnitConfiguration.Data persistentData = configuration.getPersistentData();
    persistentData.setScope(TestSearchScope.WHOLE_PROJECT);
    persistentData.PACKAGE_NAME = "p";
    persistentData.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    final ExecutionEnvironment environment =
      ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build();
    final TestPackage aPackage = new TestPackage(configuration, environment);
    aPackage.createSearchingForTestsTask().startSearch();
    final File workingDirsFile = aPackage.getWorkingDirsFile();
    assertNotNull(workingDirsFile);
    assertEmpty(FileUtil.loadFile(workingDirsFile));
  }

  private Module setupModule(String moduleName, final String className) throws IOException {
    final VirtualFile root1 = myFixture.getTempDirFixture().findOrCreateDir(moduleName);
    final Module module = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), moduleName, root1);
    PsiTestUtil.removeAllRoots(module, IdeaTestUtil.getMockJdk18());
    PsiTestUtil.addSourceRoot(module, root1, true);
    myFixture.addFileToProject(moduleName + "/p/" + className + ".java",
                               "package p;\n" +
                               "public class " + className + " extends junit.framework.TestCase {\n" +
                               "  public void testName1(){}" +
                               "}");
    final String pathForClass = PathUtil.getJarPathForClass(TestCase.class);
    PsiTestUtil.addLibrary(module,
                           "junit4",
                           StringUtil.getPackageName(pathForClass, File.separatorChar),
                           StringUtil.getShortName(pathForClass, File.separatorChar));
    return module;
  }

  private static String preparePathsForComparison(String fileContent, Module mod1, Module mod2) {
    fileContent = FileUtil.toSystemIndependentName(fileContent);
    fileContent = replace(fileContent, ModuleRootManager.getInstance(mod1).getContentRoots()[0].getPath(), "MODULE_1");
    fileContent = replace(fileContent, ModuleRootManager.getInstance(mod2).getContentRoots()[0].getPath(), "MODULE_2");
    fileContent = StringUtil.convertLineSeparators(fileContent);
    final String[] lines = fileContent.split("\n");
    lines[3] = "CLASSPATH";
    lines[9] = "CLASSPATH";
    return StringUtil.join(lines, "\n");
  }

  private static String replace(String fileContent, String regex, String home) {
    return fileContent.replaceAll(StringUtil.escapePattern(FileUtil.toSystemIndependentName(regex)), home);
  }
}
