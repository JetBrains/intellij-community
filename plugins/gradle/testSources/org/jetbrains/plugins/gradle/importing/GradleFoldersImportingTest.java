/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testBaseJavaProject() throws Exception {

    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleOutputs("project_main",
                        getProjectPath() + "/build/classes/main",
                        getProjectPath() + "/build/resources/main");
    assertModuleOutputs("project_test",
                        getProjectPath() + "/build/classes/test",
                        getProjectPath() + "/build/resources/test");

    assertModuleOutput("project_main", getProjectPath() + "/build/classes/main", "");
    assertModuleOutput("project_test", "", getProjectPath() + "/build/classes/test");
  }

  @Test
  public void testCompileOutputPathCustomizedWithIdeaPlugin() throws Exception {

    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    outputDir = file(buildDir)\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleOutput("project_main", getProjectPath() + "/build", "");
    assertModuleOutput("project_test", "", getProjectPath() + "/build/classes/test");
  }

  @Test
  @TargetVersions("2.2+")
  public void testSourceGeneratedFoldersWithIdeaPlugin() throws Exception {

    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    generatedSourceDirs += file('src/main/java')\n" +
      "    generatedSourceDirs += file('src/test/java')\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");
    assertGeneratedSources("project_main", "java");
    assertGeneratedTestSources("project_test", "java");
  }

  @Test
  public void testProjectWithInheritedOutputDirs() throws Exception {

    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    inheritOutputDirs = true\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleInheritedOutput("project");
    assertModuleInheritedOutput("project_main");
    assertModuleInheritedOutput("project_test");
  }

  protected void assertDefaultGradleJavaProjectFolders(@NotNull String mainModuleName) {
    assertExcludes(mainModuleName, ".gradle", "build");
    final String mainSourceSetModuleName = mainModuleName + "_main";
    assertContentRoots(mainSourceSetModuleName, getProjectPath() + "/src/main");
    assertSources(mainSourceSetModuleName, "java");
    assertResources(mainSourceSetModuleName, "resources");
    final String testSourceSetModuleName = mainModuleName + "_test";
    assertContentRoots(testSourceSetModuleName, getProjectPath() + "/src/test");
    assertTestSources(testSourceSetModuleName, "java");
    assertTestResources(testSourceSetModuleName, "resources");
  }
}
