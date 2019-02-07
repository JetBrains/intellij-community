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

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Vladislav.Soroka
 */
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testBaseJavaProject() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(ThreeState.NO);
    createDefaultDirs();
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");


    final String mainClassesOutputPath = "/out/production/classes";
    assertModuleOutputs("project.main",
                        getProjectPath() + mainClassesOutputPath,
                        getProjectPath() + "/out/production/resources");
    String testClassesOutputPath = "/out/test/classes";
    assertModuleOutputs("project.test",
                        getProjectPath() + testClassesOutputPath,
                        getProjectPath() + "/out/test/resources");

    assertModuleOutput("project.main", getProjectPath() + mainClassesOutputPath, "");
    assertModuleOutput("project.test", "", getProjectPath() + testClassesOutputPath);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleOutputs("project",
                        getProjectPath() + "/out/production/classes",
                        getProjectPath() + "/out/production/resources",
                        getProjectPath() + "/out/test/classes",
                        getProjectPath() + "/out/test/resources");

    assertModuleOutput("project", getProjectPath() + "/out/production/classes", getProjectPath() + "/out/test/classes");
  }

  @Test
  public void testCompileOutputPathCustomizedWithIdeaPlugin() throws Exception {
    createDefaultDirs();
    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    outputDir = file(buildDir)\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleOutput("project.main", getProjectPath() + "/build", "");
    String testClassesOutputPath = "/out/test/classes";
    assertModuleOutput("project.test", "", getProjectPath() + testClassesOutputPath);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleOutput("project", getProjectPath() + "/build", getProjectPath() + "/out/test/classes");
  }

  @Test
  @TargetVersions("2.2+")
  public void testSourceGeneratedFoldersWithIdeaPlugin() throws Exception {
    createDefaultDirs();
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

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");
    assertGeneratedSources("project.main", "java");
    assertGeneratedTestSources("project.test", "java");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");
    assertGeneratedSources("project", "src/main/java");
    assertGeneratedTestSources("project", "src/test/java");
  }

  @Test
  public void testCustomSourceSetsAreImported() throws Exception {
    createDefaultDirs();
    createProjectSubFile("src/generated/java/G.java");

    importProject("" +
                  "apply plugin: 'java'\n" +
                  "apply plugin: 'idea'\n" +
                  "\n" +
                  "sourceSets {\n" +
                  "  generated\n" +
                  "}");

    assertModules("project", "project.main", "project.test", "project.generated");

    importProjectUsingSingeModulePerGradleProject();
    assertSources("project", "src/generated/java", "src/main/java");
    assertTestSources("project", "src/test/java");
  }

  @Test
  @TargetVersions("4.7+")
  public void testResourceFoldersWithIdeaPlugin() throws Exception {
    createProjectSubDirs("src/main/java",
                         "src/main/src2",
                         "src/main/resources",
                         "src/main/resources2",
                         "src/test/java",
                         "src/test/src2",
                         "src/test/resources",
                         "src/test/resources2");
    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    sourceDirs += file('src/main/src2')\n" +
      "    resourceDirs += file('src/main/resources2')\n" +
      "    testSourceDirs += file('src/test/src2')\n" +
      "    testResourceDirs += file('src/test/resources2')\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build", "out");
    assertContentRoots("project.main", getProjectPath() + "/src/main");
    assertSources("project.main", "java", "src2");
    assertResources("project.main", "resources", "resources2");
    assertContentRoots("project.test", getProjectPath() + "/src/test");
    assertTestSources("project.test", "java", "src2");
    assertTestResources("project.test", "resources", "resources2");

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build", "out");
    assertSources("project", "src/main/java", "src/main/src2");
    assertResources("project", "src/main/resources", "src/main/resources2");
    assertTestSources("project", "src/test/java", "src/test/src2");
    assertTestResources("project", "src/test/resources", "src/test/resources2");
  }

  @Test
  public void testProjectWithInheritedOutputDirs() throws Exception {

    createDefaultDirs();
    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    inheritOutputDirs = true\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleInheritedOutput("project");
    assertModuleInheritedOutput("project.main");
    assertModuleInheritedOutput("project.test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFoldersForMergedModule("project");

    assertModuleInheritedOutput("project");
  }

  @Test
  public void testSourceFoldersMerge() throws Exception {

    createDefaultDirs();
    createProjectSubFile("src/B.java");
    createProjectSubFile("src/resources/res.properties");
    createProjectSubFile("test/BTest.java");
    createProjectSubFile("test/resources/res_test.properties");

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

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build", "out");
    final String mainSourceSetModuleName = "project.main";
    assertContentRoots(mainSourceSetModuleName, getProjectPath() + "/src");
    assertSources(mainSourceSetModuleName, "", "main/java");
    assertResources(mainSourceSetModuleName, "main/resources", "resources");
    final String testSourceSetModuleName = "project.test";
    assertContentRoots(testSourceSetModuleName, getProjectPath() + "/test", getProjectPath() + "/src/test");
    assertTestSources(testSourceSetModuleName, "src/test/java", "test");
    assertTestResources(testSourceSetModuleName, "src/test/resources", "test/resources");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build", "out");
    assertSources("project", "src", "src/main/java");
    assertResources("project", "src/main/resources", "src/resources");
    assertTestSources("project", "src/test/java", "test");
    assertTestResources("project", "src/test/resources", "test/resources");
  }

  @Test
  public void testRootsAreNotCreatedIfFilesAreMissing() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/resources/res.properties");
    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'"
    );

    assertModules("project");
    assertExcludes("project", ".gradle", "build", "out");
    assertSources("project", "src/main/java");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project", "src/test/resources");
  }

  @Test
  public void testRootsAreAddedWhenAFolderCreated() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'");

    assertModules("project");
    assertSources("project", "src/main/java");
    assertTestSources("project");

    createProjectSubFile("src/test/java/ATest.java");
    assertTestSources("project", "src/test/java");

    createProjectSubFile("src/main/resources/res.txt");
    assertResources("project", "src/main/resources");
  }

  @Test
  public void testRootsListenersAreUpdatedWithProjectModel() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'");

    assertModules("project");

    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "sourceSets {\n" +
      " test {\n" +
      "    java.srcDirs = [file('test-src/java')]" +
      "  }\n" +
      "}");

    createProjectSubFile("src/test/java/ATest.java");
    createProjectSubFile("test-src/java/BTest.java");

    assertTestSources("project", "test-src/java");
  }


  @Test
  public void testSourceAndResourceFoldersCollision() throws Exception {
    createProjectSubFile("src/A.java");
    createProjectSubFile("src/production.properties");
    createProjectSubFile("test/Test.java");
    createProjectSubFile("test/test.properties");

    importProject("apply plugin: 'java'\n" +
                  "sourceSets {\n" +
                  "  main {\n" +
                  "    java {\n" +
                  "      srcDir 'src'\n" +

                  "    }\n" +
                  "    resources {\n" +
                  "      srcDir 'src'\n" +
                  "    }\n" +
                  "  }\n" +
                  "  test {\n" +
                  "    java {\n" +
                  "      srcDir 'test'\n" +
                  "    }\n" +
                  "    resources {\n" +
                  "      srcDir 'test'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n");
    assertModules("project", "project.main", "project.test");
    assertSources("project.main", "");
    // assert relative to linked project path because several content roots are created for "project.test" module
    assertTestSources("project.test", "test");

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertSources("project", "src");
    assertTestSources("project", "test");
    assertResources("project");
    assertTestResources("project");
  }

  @Test
  public void testModuleGroupingFollowGradleProjectStructure() throws Exception {
    /*
    - Gradle project hierarchy
    project
       \--- project1
              \--- project2
              \--- project3

    - Folder hierarchy
     project
        \--- project1
        |       \--- project2
        \--- project3
                \--- src
                       \--- main
                       \--- test
     */
    createProjectSubFile("settings.gradle"
      , "include (':project1', ':project1:project2', ':project1:project3')\n" +
        "project(':project1:project3').projectDir = file('project3')\n" +
        "rootProject.name = 'rootName'");

    createProjectSubFile("build.gradle",
                         "project(':').group = 'my.test.rootProject.group'\n" +
                         "project(':project1').group = 'my.test.project1.group'\n" +
                         "project(':project1:project2').group = 'my.test.project2.group'\n" +
                         "project(':project1:project3').group = 'my.test.project3.group'");

    createProjectSubFile("project1/build.gradle");
    createProjectSubFile("project1/project2/build.gradle");
    createProjectSubFile("project3/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("project3/src/main/java/AClass.java");
    createProjectSubFile("project3/src/test/java/AClassTest.java");

    importProject();

    assertModules("rootName",
                  "rootName.project1",
                  "rootName.project1.project2",
                  "rootName.project1.project3",
                  "rootName.project1.project3.main",
                  "rootName.project1.project3.test");
    assertContentRoots("rootName.project1.project3",
                       FileUtil.toSystemIndependentName(new File(getProjectPath(), "project3").getAbsolutePath()));

    getCurrentExternalProjectSettings().setUseQualifiedModuleNames(false);

    importProject();
    assertModules("rootName",
                  "project1",
                  "project2",
                  "project3",
                  "project3_main",
                  "project3_test");

    assertContentRoots("project3",
                       FileUtil.toSystemIndependentName(new File(getProjectPath(), "project3").getAbsolutePath()));

    assertModuleGroupPath("rootName", "rootName");
    assertModuleGroupPath("project1", "rootName", "project1");
    assertModuleGroupPath("project2", "rootName", "project1", "project2");
    assertModuleGroupPath("project3", "rootName", "project1", "project3");
  }

  protected void assertDefaultGradleJavaProjectFolders(@NotNull String mainModuleName) {
    assertExcludes(mainModuleName, ".gradle", "build", "out");
    final String mainSourceSetModuleName = mainModuleName + ".main";
    assertContentRoots(mainSourceSetModuleName, getProjectPath() + "/src/main");
    assertSources(mainSourceSetModuleName, "java");
    assertResources(mainSourceSetModuleName, "resources");
    final String testSourceSetModuleName = mainModuleName + ".test";
    assertContentRoots(testSourceSetModuleName, getProjectPath() + "/src/test");
    assertTestSources(testSourceSetModuleName, "java");
    assertTestResources(testSourceSetModuleName, "resources");
  }

  protected void assertDefaultGradleJavaProjectFoldersForMergedModule(@NotNull String moduleName) {
    assertContentRoots(moduleName, getProjectPath());
    assertExcludes(moduleName, ".gradle", "build", "out");
    assertSources(moduleName, "src/main/java");
    assertResources(moduleName, "src/main/resources");
    assertTestSources(moduleName, "src/test/java");
    assertTestResources(moduleName, "src/test/resources");
  }

  private void createDefaultDirs() throws IOException {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/java/A.java");
    createProjectSubFile("src/main/resources/resource.properties");
    createProjectSubFile("src/test/resources/test_resource.properties");
  }

  private void assertTestSources(Project project, String moduleName, String... expected) {
    final Module fooModule = getModule(project, moduleName);
    final ContentEntry[] contentRoots = ModuleRootManager.getInstance(fooModule).getContentEntries();
    String rootUrl = contentRoots.length > 1 ? ExternalSystemApiUtil.getExternalProjectPath(fooModule) : null;
    doAssertContentFolders(rootUrl, contentRoots, JavaSourceRootType.TEST_SOURCE, expected);
  }
}
