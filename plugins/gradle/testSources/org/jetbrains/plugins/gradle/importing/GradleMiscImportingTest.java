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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.list;

/**
 * @author Vladislav.Soroka
 * @since 10/27/2015
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleMiscImportingTest extends GradleImportingTestCase {

  /**
   * It's sufficient to run the test against one gradle version
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() throws Throwable {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Test
  public void testTestModuleProperties() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project_main", "project_test");

    final Module testModule = getModule("project_test");
    TestModuleProperties testModuleProperties = TestModuleProperties.getInstance(testModule);
    assertEquals("project_main", testModuleProperties.getProductionModuleName());

    final Module productionModule = getModule("project_main");
    assertSame(productionModule, testModuleProperties.getProductionModule());
  }

  @Test
  public void testTestModulePropertiesForModuleWithHyphenInName() throws Exception {
    createSettingsFile("rootProject.name='my-project'");
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("my-project", "my-project_main", "my-project_test");

    final Module testModule = getModule("my-project_test");
    TestModuleProperties testModuleProperties = TestModuleProperties.getInstance(testModule);
    assertEquals("my-project_main", testModuleProperties.getProductionModuleName());
  }

  @Test
  public void testInheritProjectJdkForModules() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project_main", "project_test");
    assertTrue(ModuleRootManager.getInstance(getModule("project")).isSdkInherited());
    assertTrue(ModuleRootManager.getInstance(getModule("project_main")).isSdkInherited());
    assertTrue(ModuleRootManager.getInstance(getModule("project_test")).isSdkInherited());
  }

  @Test
  public void testLanguageLevel() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "sourceCompatibility = 1.5\n" +
      "apply plugin: 'java'\n" +
      "compileTestJava {\n" +
      "  sourceCompatibility = 1.8\n" +
      "}\n"
    );

    assertModules("project", "project_main", "project_test");
    assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule("project_main"));
    assertEquals(LanguageLevel.JDK_1_8, getLanguageLevelForModule("project_test"));
  }

  @Test
  public void testTargetLevel() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "targetCompatibility = 1.8\n" +
      "compileJava {\n" +
      "  targetCompatibility = 1.5\n" +
      "}\n"
    );

    assertModules("project", "project_main", "project_test");
    assertEquals("1.5", getBytecodeTargetLevel("project_main"));
    assertEquals("1.8", getBytecodeTargetLevel("project_test"));

  }

  @Test
  @TargetVersions("3.4+")
  public void testJdkName() throws Exception {
    Sdk myJdk = createJdk("MyJDK");
    edt(() -> ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(myJdk)));
    try {
      importProject(
        "apply plugin: 'java'\n" +
        "apply plugin: 'idea'\n" +
        "idea {\n" +
        "  module {\n" +
        "    jdkName = 'MyJDK'\n" +
        "  }\n" +
        "}\n"
      );

      assertModules("project", "project_main", "project_test");
      assertTrue(getSdkForModule("project_main") == myJdk);
      assertTrue(getSdkForModule("project_test") == myJdk);

    } finally {
      edt(() -> ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().removeJdk(myJdk)));
    }
  }

  @Test
  public void testUnloadedModuleImport() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );
    assertModules("project", "project_main", "project_test");

    edt(() -> ModuleManager.getInstance(myProject).setUnloadedModules(list("project_main")));
    assertModules("project", "project_test");

    importProject();
    assertModules("project", "project_test");
  }

  @Test
  public void testCompilerConfigurationSettingsImport() throws Exception {
    importProject(
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    compiler {\n" +
      "      resourcePatterns '!*.java;!*.class'\n" +
      "      clearOutputDirectory false\n" +
      "      addNotNullAssertions false\n" +
      "      autoShowFirstErrorInEditor false\n" +
      "      displayNotificationPopup false\n" +
      "      enableAutomake false\n" +
      "      parallelCompilation true\n" +
      "      rebuildModuleOnDependencyChange false\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);

    assertSameElements(compilerConfiguration.getResourceFilePatterns(), "!*.class", "!*.java");
    assertFalse(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    assertFalse(compilerConfiguration.isAddNotNullAssertions());
    assertFalse(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    assertFalse(workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP);
    assertFalse(workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    assertTrue(workspaceConfiguration.PARALLEL_COMPILATION);
    assertFalse(workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE);
  }

  private LanguageLevel getLanguageLevelForModule(final String moduleName) {
    return LanguageLevelModuleExtensionImpl.getInstance(getModule(moduleName)).getLanguageLevel();
  }

  private String getBytecodeTargetLevel(String moduleName) {
    return CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule(moduleName));
  }

  private Sdk getSdkForModule(final String moduleName) {
    return ModuleRootManager.getInstance(getModule(moduleName)).getSdk();
  }
}
