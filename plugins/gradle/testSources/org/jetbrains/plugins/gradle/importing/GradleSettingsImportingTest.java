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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.impl.invalid.InvalidFacet;
import com.intellij.openapi.module.ModuleManager;
import org.junit.Test;

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
public class GradleSettingsImportingTest extends GradleImportingTestCase {

  @Test
  public void testCompilerConfigurationSettingsImport() throws Exception {
    final String pathToPlugin = getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();

    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + pathToPlugin + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
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

  @Test
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    final String pathToPlugin = getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();

    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + pathToPlugin + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  module.settings {\n" +
      "    runConfigurations {\n" +
      "       app1 {\n" +
      "           type = 'application'\n" +
      "           mainClass = 'my.app.Class'\n" +
      "           jvmArgs =   '-Xmx1g'\n" +
      "       }\n" +
      "       app2 {\n" +
      "           type = 'application'\n" +
      "           mainClass = 'my.app.Class2'\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final RunManager runManager = RunManager.getInstance(myProject);
    final RunnerAndConfigurationSettings app1 = runManager.findConfigurationByName("app1");
    final RunnerAndConfigurationSettings app2 = runManager.findConfigurationByName("app2");

    assertEquals("com.intellij.execution.application.ApplicationConfiguration", app1.getConfiguration().getClass().getCanonicalName());
    assertEquals("com.intellij.execution.application.ApplicationConfiguration", app2.getConfiguration().getClass().getCanonicalName());
  }

  @Test
  public void testFacetSettingsImport() throws Exception {
    final String pathToPlugin = getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();

    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + pathToPlugin + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  module.settings {\n" +
      "    facets {\n" +
      "       invalid {\n" +
      "           errorMessage 'Hello World!'\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final Facet[] facets = FacetManager.getInstance(ModuleManager.getInstance(myProject).getModules()[0]).getAllFacets();

    assertSize(1, facets);
    assertEquals("Hello World!", ((InvalidFacet)facets[0]).getErrorMessage());
  }

}
