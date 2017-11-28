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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JUnitClasspathTest extends JavaCodeInsightFixtureTestCase {

  public void testWorkingDirsFileWhenConfigurationSpansToMultipleModules() throws Exception {
    final Module mod1 = setupModule("mod1", "T1");
    final Module mod2 = setupModule("mod2", "T2");
    CompilerTester compiler = new CompilerTester(myFixture.getProject(), Arrays.asList(ModuleManager.getInstance(myFixture.getProject()).getModules()));
    compiler.rebuild();
    try {
      final JUnitConfiguration configuration =
        new JUnitConfiguration("p", getProject(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
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
      assertEquals("p\n" +
                   "MODULE_1\n" +
                   "mod1\n" +
                   "CLASSPATH\n" +
                   "1\n" +
                   "p.T1\n" +
                   "MODULE_2\n" +
                   "mod2\n" +
                   "CLASSPATH\n" +
                   "1\n" +
                   "p.T2", file);
    }
    finally {
      compiler.tearDown();
    }
  }

  public void testNoWorkingDirsFileWhenOnlyOneModuleExist() throws Exception {
    setupModule("mod1", "T1");
    CompilerTester compiler = new CompilerTester(getProject(), Arrays.asList(ModuleManager.getInstance(getProject()).getModules()));
    compiler.rebuild();
    try {
      final JUnitConfiguration configuration =
        new JUnitConfiguration("p", getProject(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
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
    finally {
      compiler.tearDown();
    }
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
    lines[8] = "CLASSPATH";
    return StringUtil.join(lines, "\n");
  }

  private static String replace(String fileContent, String regex, String home) {
    return fileContent.replaceAll(StringUtil.escapePattern(FileUtil.toSystemIndependentName(regex)), home);
  }
}
