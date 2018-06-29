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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testBaseJavaProject() throws Exception {

    createDefaultDirs();
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");


    final String mainClassesOutputPath = "/out/production/classes";
    assertModuleOutputs("project_main",
                        getProjectPath() + mainClassesOutputPath,
                        getProjectPath() + "/out/production/resources");
    String testClassesOutputPath = "/out/test/classes";
    assertModuleOutputs("project_test",
                        getProjectPath() + testClassesOutputPath,
                        getProjectPath() + "/out/test/resources");

    assertModuleOutput("project_main", getProjectPath() + mainClassesOutputPath, "");
    assertModuleOutput("project_test", "", getProjectPath() + testClassesOutputPath);

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

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleOutput("project_main", getProjectPath() + "/build", "");
    String testClassesOutputPath = "/out/test/classes";
    assertModuleOutput("project_test", "", getProjectPath() + testClassesOutputPath);

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

    assertModules("project", "project_main", "project_test");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build", "out");
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
  public void testRootsListenersRestoredWhenProjectOpen() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'");

    @SystemIndependent final String path = myProject.getProjectFilePath();

    edt(() -> {
      VirtualFileManager.getInstance().syncRefresh();
      UIUtil.dispatchAllInvocationEvents();
      PlatformTestUtil.saveProject(myProject);
      ProjectManagerEx.getInstanceEx().closeProject(myProject);
      UIUtil.dispatchAllInvocationEvents();
    });

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final Ref<Project> projectRef = new Ref<>();
    try {
      projectRef.set(projectManager.loadProject(path));
      edt(() -> projectManager.openTestProject(projectRef.get()));

      createProjectSubFile("src/test/java/ATest.java");
      assertTestSources(projectRef.get(), "project", "src/test/java");
    } finally {
      if (!projectRef.isNull()) {
        edt(() ->{
          projectManager.closeTestProject(projectRef.get());
          WriteAction.run(() -> Disposer.dispose(projectRef.get()));
        });
      }
    }
  }


  protected void assertDefaultGradleJavaProjectFolders(@NotNull String mainModuleName) {
    assertExcludes(mainModuleName, ".gradle", "build", "out");
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
