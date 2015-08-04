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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class JUnitClasspathTest extends JavaCodeInsightFixtureTestCase {

  public void testWorkingDirsFileWhenConfigurationSpansToMultipleModules() throws Exception {
    final Module mod1 = setupModule("mod1", "T1");
    final Module mod2 = setupModule("mod2", "T2");

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
    final RegistryValue smRunnerProperty = Registry.get("junit_sm");
    final boolean oldValue = smRunnerProperty.asBoolean();
    try {
      //check old format
      smRunnerProperty.setValue(false);
      aPackage.createSearchingForTestsTask().startSearch();
      workingDirsFile = aPackage.getWorkingDirsFile();
      assertNotNull(workingDirsFile);
      String file = preparePathsForComparison(FileUtil.loadFile(workingDirsFile), mod1, mod2);
      assertEquals("p\n" +
                   "MODULE_1\n" +
                   "mod1\n" +
                   "IDEA_HOME/lib/junit-4.12.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/annotations.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/rt.jar\n" +
                   "1\n" +
                   "p.T1\n" +
                   "MODULE_2\n" +
                   "mod2\n" +
                   "IDEA_HOME/lib/junit-4.12.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/annotations.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/rt.jar\n" +
                   "1\n" +
                   "p.T2\n", file);

      //check sm runner
      smRunnerProperty.setValue(true);
      aPackage.createSearchingForTestsTask().startSearch();
      workingDirsFile = aPackage.getWorkingDirsFile();
      assertNotNull(workingDirsFile);
      file = preparePathsForComparison(FileUtil.loadFile(workingDirsFile), mod1, mod2);
      assertEquals("p\n" +
                   "MODULE_1\n" +
                   "mod1\n" +
                   "IDEA_HOME/lib/junit-4.12.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/annotations.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/rt.jar\n" +
                   "1\n" +
                   "p.T1\n" +
                   "MODULE_2\n" +
                   "mod2\n" +
                   "IDEA_HOME/lib/junit-4.12.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/annotations.jar;IDEA_HOME/java/mockJDK-1.8/jre/lib/rt.jar\n" +
                   "1\n" +
                   "p.T2\n", file);
    }
    finally {
      smRunnerProperty.setValue(oldValue);
    }
  }

  public void testNoWorkingDirsFileWhenOnlyOneModuleExist() throws Exception {
    setupModule("mod1", "T1");
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
    fileContent = replace(fileContent, PathUtil.getJarPathForClass(ServiceMessageTypes.class), "SERVICE_MESSAGES");
    fileContent = fileContent.replaceAll(FileUtil.toSystemIndependentName(PathUtil.getJarPathForClass(JUnitStarter.class)) + File.pathSeparator, "");
    fileContent = fileContent.replaceAll(FileUtil.toSystemIndependentName(JavaSdkUtil.getIdeaRtJarPath()) + File.pathSeparator, "");
    fileContent = replace(fileContent, PathManager.getHomePath() + "/community", "IDEA_HOME");
    fileContent = replace(fileContent, PathManager.getHomePath(), "IDEA_HOME");
    fileContent = fileContent.replaceAll(File.pathSeparator, ";");
    return StringUtil.convertLineSeparators(fileContent);
  }

  private static String replace(String fileContent, String regex, String home) {
    return fileContent.replaceAll(FileUtil.toSystemIndependentName(regex), home);
  }
}
