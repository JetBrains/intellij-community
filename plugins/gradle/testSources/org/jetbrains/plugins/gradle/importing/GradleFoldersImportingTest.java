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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleOutputs("project",
                        getProjectPath() + "/build/classes/main",
                        getProjectPath() + "/build/resources/main",
                        getProjectPath() + "/build/classes/test",
                        getProjectPath() + "/build/resources/test");

    assertModuleOutput("project", getProjectPath() + "/build/classes/main", getProjectPath() + "/build/classes/test");
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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleOutput("project", getProjectPath() + "/build", getProjectPath() + "/build/classes/test");
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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");
    assertGeneratedSources("project", "src/main/java");
    assertGeneratedTestSources("project", "src/test/java");
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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleInheritedOutput("project");
  }

  @Test
  public void testSourceFoldersMerge() throws Exception {

    importProject(
      "apply plugin: 'java'\n" +
      "sourceSets {\n" +
      "  main {\n" +
      "    resources.srcDir 'src/resources'\n" +
      "    java.srcDir 'src'\n" +
      "  }\n" +
      "  test {\n" +
      "    resources.srcDir 'test/resources'\n" +
      "    java.srcDir 'test'\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build");
    final String mainSourceSetModuleName = "project_main";
    assertContentRoots(mainSourceSetModuleName, getProjectPath() + "/src");
    assertSources(mainSourceSetModuleName, "", "main/java");
    assertResources(mainSourceSetModuleName, "main/resources", "resources");
    final String testSourceSetModuleName = "project_test";
    assertContentRoots(testSourceSetModuleName, getProjectPath() + "/test", getProjectPath() + "/src/test");
    assertTestSources(testSourceSetModuleName, "src/test/java", "test");
    assertTestResources(testSourceSetModuleName, "src/test/resources", "test/resources");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build");
    assertSources("project", "src", "src/main/java");
    assertResources("project", "src/main/resources", "src/resources");
    assertTestSources("project", "src/test/java", "test");
    assertTestResources("project", "src/test/resources", "test/resources");
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

  protected void assertDefaultGradleJavaProjectFoldersForMergedModule(@NotNull String moduleName) {
    assertContentRoots(moduleName, getProjectPath());
    assertExcludes(moduleName, ".gradle", "build");
    assertSources(moduleName, "src/main/java");
    assertResources(moduleName, "src/main/resources");
    assertTestSources(moduleName, "src/test/java");
    assertTestResources(moduleName, "src/test/resources");
  }
}
