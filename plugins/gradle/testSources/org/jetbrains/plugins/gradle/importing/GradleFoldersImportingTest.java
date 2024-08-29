// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder;
import org.jetbrains.plugins.gradle.service.project.data.GradleExcludeBuildFilesDataService;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getManager;

/**
 * @author Vladislav.Soroka
 */
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testUnsupportedTypesInDsl() throws Exception {
    importProject(
      createBuildScriptBuilder().addPostfix(
          "import org.gradle.api.internal.FactoryNamedDomainObjectContainer;",
          "import org.gradle.internal.reflect.Instantiator;",
          "class MyObj implements Named {",
          "  String myName;",
          "  public MyObj(String name) {",
          "    myName = namse",
          "  }",
          "  ",
          "  public String getName() {",
          "    return myName",
          "  }",
          "}",
          "project.extensions.create(",
          "                \"sourceSets\",",
          "                FactoryNamedDomainObjectContainer,",
          "                MyObj,",
          "                services.get(Instantiator),",
          "               {action -> }",
          "        ) ",
          "sourceSets {",
          " println \"Hello World!\"",
          "}"
      ).generate());
  }

  @Test
  public void testBaseJavaProject() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    createDefaultDirs();
    importProject(
      "apply plugin: 'java'"
    );
    assertNotDelegatedBaseJavaProject();

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();
    assertNotDelegatedMergedBaseJavaProject();

    getCurrentExternalProjectSettings().setDelegatedBuild(true);
    importProject();
    assertDelegatedMergedBaseJavaProject();

    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    // subscribe to the GradleSettings changes topic
    ((GradleManager)getManager(GradleConstants.SYSTEM_ID)).runActivity(myProject);
    GradleSettings.getInstance(myProject).getPublisher().onBuildDelegationChange(false, getProjectPath());
    assertNotDelegatedMergedBaseJavaProject();

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();
    assertNotDelegatedBaseJavaProject();
    getCurrentExternalProjectSettings().setDelegatedBuild(true);
    GradleSettings.getInstance(myProject).getPublisher().onBuildDelegationChange(true, getProjectPath());
    assertDelegatedBaseJavaProject();
  }

  @Test
  @TargetVersions("5.6+")
  public void testBaseJavaProjectHasNoWarnings() throws Exception {
    createDefaultDirs();
    createProjectSubFile("gradle.properties", "org.gradle.warning.mode=fail");
    importProject("apply plugin: 'java'");

    assertDelegatedBaseJavaProject();
  }

  private void assertNotDelegatedBaseJavaProject() {
    assertModules("project", "project.main", "project.test");
    assertNoExcludePatterns("project", "build.gradle");

    assertDefaultGradleJavaProjectFolders();

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
  }

  private void assertDelegatedBaseJavaProject() {
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());
    assertNoExcludePatterns("project", "build.gradle");

    assertModuleOutputs("project.main",
                        getProjectPath() + "/build/classes/java/main",
                        getProjectPath() + "/build/resources/main");
    assertModuleOutput("project.main", getProjectPath() + "/build/classes/java/main", "");

    assertModuleOutputs("project.test",
                        getProjectPath() + "/build/classes/java/test",
                        getProjectPath() + "/build/resources/test");
    assertModuleOutput("project.test", "", getProjectPath() + "/build/classes/java/test");
  }

  private void assertNotDelegatedMergedBaseJavaProject() {
    assertModules("project");

    assertDefaultGradleJavaProjectFoldersForMergedModule();

    assertModuleOutputs("project",
                        getProjectPath() + "/out/production/classes",
                        getProjectPath() + "/out/production/resources",
                        getProjectPath() + "/out/test/classes",
                        getProjectPath() + "/out/test/resources");

    assertModuleOutput("project", getProjectPath() + "/out/production/classes", getProjectPath() + "/out/test/classes");
  }

  private void assertDelegatedMergedBaseJavaProject() {
    assertModuleOutputs("project",
                        getProjectPath() + "/build/classes/java/main",
                        getProjectPath() + "/build/resources/main",
                        getProjectPath() + "/build/classes/java/test",
                        getProjectPath() + "/build/resources/test");
  }

  @Test
  public void testCompileOutputPathCustomizedWithIdeaPlugin() throws Exception {
    createDefaultDirs();
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            outputDir = file(buildDir)
          }
        }"""
    );

    assertModules("project", "project.main", "project.test");

    assertDefaultGradleJavaProjectFolders();

    assertModuleOutput("project.main", getProjectPath() + "/build/classes/java/main", "");
    assertModuleOutput("project.test", "", getProjectPath() + "/build/classes/java/test");

    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    GradleSettings.getInstance(myProject).getPublisher().onBuildDelegationChange(false, getProjectPath());
    assertModuleOutput("project.main", getProjectPath() + "/build", "");
    assertModuleOutput("project.test", "", getProjectPath() + "/out/test/classes");

    getCurrentExternalProjectSettings().setDelegatedBuild(true);

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");

    assertDefaultGradleJavaProjectFoldersForMergedModule();

    assertModuleOutput("project", getProjectPath() + "/build", getProjectPath() + "/build/classes/java/test");
  }

  @Test
  public void testSourceGeneratedFoldersWithIdeaPlugin() throws Exception {
    createDefaultDirs();
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            generatedSourceDirs += file('src/main/java')
            generatedSourceDirs += file('src/test/java')
          }
        }"""
    );

    assertModules("project", "project.main", "project.test");

    assertDefaultGradleJavaProjectFolders();
    assertGeneratedSources("project.main", path("src/main/java"));
    assertGeneratedTestSources("project.test", path("src/test/java"));

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");

    assertDefaultGradleJavaProjectFoldersForMergedModule();
    assertGeneratedSources("project", path("src/main/java"));
    assertGeneratedTestSources("project", path("src/test/java"));
  }

  @Test
  public void testCustomSourceSetsAreImported() throws Exception {
    createDefaultDirs();
    createProjectSubFile("src/generated/java/G.java");

    importProject("""
                    apply plugin: 'java'
                    apply plugin: 'idea'

                    sourceSets {
                      generated
                    }""");

    assertModules("project", "project.main", "project.test", "project.generated");

    importProjectUsingSingeModulePerGradleProject();
    assertSources("project", path("src/generated/java"), path("src/main/java"));
    assertTestSources("project", path("src/test/java"));
  }

  @Test
  @TargetVersions("4.7+")
  public void testResourceFoldersWithIdeaPluginInNonJavaProject() throws Exception {
    createProjectSubDirs("python/src", "python/test", "python/resources");
    importProject(createBuildScriptBuilder().withIdeaPlugin()
                    .addPostfix(      "idea {",
                                      "  module {",
                                      "    sourceDirs += file('python/src')",
                                      "    resourceDirs += file('python/resources')",
                                      "    testSourceDirs += file('python/test')",
                                      "  }",
                                      "}")
                    .generate());

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", path("python/src"));
    assertResources("project", path("python/resources"));
    assertTestSources("project", path("python/test"));
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
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            sourceDirs += file('src/main/src2')
            resourceDirs += file('src/main/resources2')
            testSourceDirs += file('src/test/src2')
            testResourceDirs += file('src/test/resources2')
          }
        }"""
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build");
    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"), path("src/main/src2"));
    assertResources("project.main", path("src/main/resources"), path("src/main/resources2"));
    assertContentRoots("project.test", path("src/test"));
    assertTestSources("project.test", path("src/test/java"), path("src/test/src2"));
    assertTestResources("project.test", path("src/test/resources"), path("src/test/resources2"));

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build");
    assertSources("project", path("src/main/java"), path("src/main/src2"));
    assertResources("project", path("src/main/resources"), path("src/main/resources2"));
    assertTestSources("project", path("src/test/java"), path("src/test/src2"));
    assertTestResources("project", path("src/test/resources"), path("src/test/resources2"));
  }

  @Test
  @TargetVersions("4.7+")
  public void testSourceFoldersTypeAfterReimport() throws Exception {
    createProjectSubDirs("src/main/java",
                         "src/main/resources",
                         "src/test/java",
                         "src/test/src2",
                         "src/test/resources",
                         "src/test/resources2",
                         "src/customSourceSet/java",
                         "src/customSourceSet/resources");
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        sourceSets {
            customSourceSet
        }
        idea {
          module {
            testSourceDirs += file('src/test/src2')
            testResourceDirs += file('src/test/resources2')
            testSourceDirs += project.sourceSets.customSourceSet.java.srcDirs
            testResourceDirs += project.sourceSets.customSourceSet.resources.srcDirs
          }
        }"""
    );

    Runnable check = () -> {
      assertModules("project", "project.main", "project.test", "project.customSourceSet");
      assertContentRoots("project", getProjectPath());
      assertExcludes("project", ".gradle", "build");

      assertContentRoots("project.main", path("src/main"));
      assertSources("project.main", path("src/main/java"));
      assertResources("project.main", path("src/main/resources"));

      assertContentRoots("project.test", path("src/test"));
      assertTestSources("project.test", path("src/test/java"), path("src/test/src2"));
      assertTestResources("project.test", path("src/test/resources"), path("src/test/resources2"));

      assertContentRoots("project.customSourceSet", path("src/customSourceSet"));
      assertTestSources("project.customSourceSet", path("src/customSourceSet/java"));
      assertTestResources("project.customSourceSet", path("src/customSourceSet/resources"));
    };

    check.run();
    markModuleSourceFolders("project.customSourceSet", JavaSourceRootType.SOURCE);
    importProject();
    check.run();
  }

  private void markModuleSourceFolders(@NotNull String moduleName, @NotNull JpsModuleSourceRootType<?> rootType) {
    doWriteAction(() -> {
      Module module = getModule(moduleName);
      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      ContentEntry[] contentEntries = model.getContentEntries();
      for (ContentEntry contentEntry : contentEntries) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          VirtualFile folderFile = sourceFolder.getFile();
          contentEntry.removeSourceFolder(sourceFolder);
          contentEntry.addSourceFolder(folderFile, rootType);
        }
      }
      model.commit();
    });
  }

  @Test
  public void testProjectWithInheritedOutputDirs() throws Exception {

    createDefaultDirs();
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = true
          }
        }"""
    );

    assertModules("project", "project.main", "project.test");

    assertDefaultGradleJavaProjectFolders();

    assertModuleInheritedOutput("project");
    assertModuleInheritedOutput("project.main");
    assertModuleInheritedOutput("project.test");

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");

    assertDefaultGradleJavaProjectFoldersForMergedModule();

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
      """
        apply plugin: 'java'
        sourceSets {
          main {
            resources.srcDir 'src/resources'
            java.srcDir 'src'
          }
          test {
            resources.srcDir 'test/resources'
            java.srcDir 'test'
          }
        }"""
    );

    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project.main", path("src"));
    assertSources("project.main", path("src"), path("src/main/java"));
    assertResources("project.main", path("src/resources"), path("src/main/resources"));

    assertContentRoots("project.test", path("test"), path("src/test"));
    assertTestSources("project.test", path("test"), path("src/test/java"));
    assertTestResources("project.test", path("test/resources"), path("src/test/resources"));

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build");
    assertSources("project", path("src"), path("src/main/java"));
    assertResources("project", path("src/main/resources"), path("src/resources"));
    assertTestSources("project", path("test"), path("src/test/java"));
    assertTestResources("project", path("src/test/resources"), path("test/resources"));
  }

  @Test
  public void testRootsAreNotCreatedIfFilesAreMissing() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/resources/res.properties");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        sourceSets.main.java.srcDirs file('src/generated/java')
        idea.module {
          generatedSourceDirs += file('src/generated/java')
        }"""
    );

    assertModules("project");
    assertExcludes("project", ".gradle", "build");
    assertSources("project", path("src/main/java"));
    assertResources("project");
    assertTestSources("project");
    assertGeneratedSources("project");
    assertTestResources("project", path("src/test/resources"));
  }

  @Test
  public void testRootsAreAddedWhenAFolderCreated() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    importProjectUsingSingeModulePerGradleProject("""
                                                    apply plugin: 'java'
                                                    apply plugin: 'idea'
                                                    sourceSets.main.java.srcDirs file('src/generated/java')
                                                    idea.module {
                                                      generatedSourceDirs += file('src/generated/java')
                                                    }""");

    assertModules("project");
    assertSources("project", path("src/main/java"));
    assertTestSources("project");

    createProjectSubFile("src/test/java/ATest.java");
    waitForModulesUpdate();
    assertTestSources("project", path("src/test/java"));

    createProjectSubFile("src/main/resources/res.txt");
    waitForModulesUpdate();
    assertResources("project", path("src/main/resources"));

    createProjectSubFile("src/generated/java/Generated.java");
    waitForModulesUpdate();
    assertGeneratedSources("project", path("src/generated/java"));
  }

  @Test
  public void testRootsListenersAreUpdatedWithProjectModel() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'");

    assertModules("project");

    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'
        sourceSets {
         test {
            java.srcDirs = [file('test-src/java')]  }
        }""");

    createProjectSubFile("src/test/java/ATest.java");
    createProjectSubFile("test-src/java/BTest.java");
    waitForModulesUpdate();
    assertTestSources("project", path("test-src/java"));
  }


  @Test
  public void testSourceAndResourceFoldersCollision() throws Exception {
    createProjectSubFile("src/A.java");
    createProjectSubFile("src/production.properties");
    createProjectSubFile("test/Test.java");
    createProjectSubFile("test/test.properties");

    importProject("""
                    apply plugin: 'java'
                    sourceSets {
                      main {
                        java {
                          srcDir 'src'
                        }
                        resources {
                          srcDir 'src'
                        }
                      }
                      test {
                        java {
                          srcDir 'test'
                        }
                        resources {
                          srcDir 'test'
                        }
                      }
                    }
                    """);
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project", getProjectPath());
    assertContentRoots("project.main", path("src"));
    assertContentRoots("project.test", path("test"), path("src/test"));
    assertSources("project.main", path("src"));
    assertTestSources("project.test", path("test"));
    assertResources("project");
    assertTestResources("project");

    importProjectUsingSingeModulePerGradleProject();

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertSources("project", path("src"));
    assertTestSources("project", path("test"));
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
      , """
                           include (':project1', ':project1:project2', ':project1:project3')
                           project(':project1:project3').projectDir = file('project3')
                           rootProject.name = 'rootName'""");

    createProjectSubFile("build.gradle",
                         """
                           project(':').group = 'my.test.rootProject.group'
                           project(':project1').group = 'my.test.project1.group'
                           project(':project1:project2').group = 'my.test.project2.group'
                           project(':project1:project3').group = 'my.test.project3.group'""");

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

  @Test
  public void testSourceFoldersOutOfContentRootWithModuleResolving() throws Exception {
    createProjectSubFile("src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/kotlin/A.kt", "class A {}");
    createProjectSubFile("../outer2/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer3/A.java", "class A {}");
    createProjectSubFile("build/generated/A.java", "class A {}");
    createProjectSubFile("../outer4/generated/A.java", "class A {}");
    GradleBuildScriptBuilder buildScript = createBuildScriptBuilder()
      .withJavaPlugin()
      .withIdeaPlugin()
      .addPrefix(
        "sourceSets {",
        "  generated.java.srcDirs += \"${buildDir}/generated\"",
        "  generated.java.srcDirs += '../outer4/generated'",
        "  main.java.srcDirs += '../outer1/src/main/java'",
        "  main.java.srcDirs += '../outer1/src/main/kotlin'",
        "  main.java.srcDirs += '../outer2/src/main/java'",
        "  main.java.srcDirs += '../outer3'",
        "}")
      .addPrefix(
        "idea {",
        "  module {",
        "    inheritOutputDirs = true",
        "    generatedSourceDirs += file(\"${buildDir}/generated\")",
        "    generatedSourceDirs += file('../outer4/generated')",
        "  }",
        "}");
    importPerSourceSet(true);
    importProject(buildScript.generate());
    assertModules("project", "project.main", "project.test", "project.generated");
    assertContentEntryExists("project", "");
    assertContentEntryExists("project.main",
                             "../outer1",
                             "../outer2",
                             "../outer3");
    assertContentEntryExists("project.generated",
                             "build/generated",
                             "../outer4");
    assertSourceExists("project.main",
                       "src/main/java",
                       "../outer1/src/main/java",
                       "../outer1/src/main/kotlin",
                       "../outer2/src/main/java",
                       "../outer3");
    assertSourceExists("project.generated",
                       "build/generated",
                       "../outer4/generated");
  }

  @Test
  public void testSourceFoldersOutOfContentRootWithoutModuleResolving() throws Exception {
    createProjectSubFile("src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/kotlin/A.kt", "class A {}");
    createProjectSubFile("../outer2/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer3/A.java", "class A {}");
    createProjectSubFile("build/generated/A.java", "class A {}");
    createProjectSubFile("../outer4/generated/A.java", "class A {}");
    GradleBuildScriptBuilder buildScript = createBuildScriptBuilder()
      .withJavaPlugin()
      .withIdeaPlugin()
      .addPrefix(
        "sourceSets {",
        "  generated.java.srcDirs += \"${buildDir}/generated\"",
        "  generated.java.srcDirs += '../outer4/generated'",
        "  main.java.srcDirs += '../outer1/src/main/java'",
        "  main.java.srcDirs += '../outer1/src/main/kotlin'",
        "  main.java.srcDirs += '../outer2/src/main/java'",
        "  main.java.srcDirs += '../outer3'",
        "}")
      .addPrefix(
        "idea {",
        "  module {",
        "    inheritOutputDirs = true",
        "    generatedSourceDirs += file(\"${buildDir}/generated\")",
        "    generatedSourceDirs += file('../outer4/generated')",
        "  }",
        "}");
    importPerSourceSet(false);
    importProject(buildScript.generate());
    assertModules("project");
    assertContentEntryExists("project",
                             "",
                             "../outer1/src/main/java",
                             "../outer1/src/main/kotlin",
                             "../outer2",
                             "../outer3",
                             "build/generated",
                             "../outer4");
    assertSourceExists("project",
                       "src/main/java",
                       "../outer1/src/main/java",
                       "../outer1/src/main/kotlin",
                       "../outer2/src/main/java",
                       "../outer3",
                       "build/generated",
                       "../outer4/generated");
  }

  @Test
  public void testMultipleSourcesConsistencyCompilerOutput() throws Exception {
    createProjectSubFile("src/main/java/A.java", "class A {}");
    createProjectSubFile("src/main/kotlin/A.kt", "class A {}");
    importPerSourceSet(false);
    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .withKotlinJvmPlugin()
        .withJavaLibraryPlugin()
        .generate()
    );
    assertModules("project");
    assertContentEntryExists("project");
    assertSourceExists("project", "src/main/java", "src/main/kotlin");
    assertModuleOutput("project", getProjectPath() + "/build/classes/java/main", getProjectPath() + "/build/classes/java/test");
  }

  @Test
  public void testExcludedFoldersWithIdeaPlugin() throws Exception {
    createProjectSubDirs("submodule");
    importProject(
      """
        apply plugin: 'idea'
        idea {
          module {
            excludeDirs += file('submodule')
          }
        }"""
    );

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build", "submodule");

    importProject(
      "apply plugin: 'idea'\n"
    );

    assertContentRoots("project", getProjectPath());
    assertExcludes("project", ".gradle", "build");
  }

  @Test
  public void testSharedSourceFolders() throws Exception {
    createProjectSubFile("settings.gradle", "include 'app1', 'app2'");
    createProjectSubFile("shared/resources/resource.txt");
    createProjectSubFile("app1/build.gradle", createBuildScriptBuilder()
      .withJavaPlugin()
      .addPostfix(
        "sourceSets {",
        "  main.resources.srcDir '../shared/resources'",
        "  }"
      )
      .generate());
    createProjectSubFile("app2/build.gradle", createBuildScriptBuilder()
      .withJavaPlugin()
      .addPostfix(
        "sourceSets {",
        "  main.resources.srcDir '../shared/resources'",
        "  }"
      )
      .generate());

    importPerSourceSet(false);
    importProject("");

    assertModules("project", "project.app1", "project.app2");
    assertResources("project.app1", getProjectPath() + "/shared/resources");
    assertResources("project.app2");
  }

  @Test
  public void testBuildFileAtSourceRootLayout() throws Exception {
    createProjectSubDir("build");
    final GradleBuildScriptBuilder buildScript = createBuildScriptBuilder()
      .withJavaPlugin()
      .addPostfix(
        "sourceSets {",
        "  main.java.srcDirs = ['.']",
        "}"
      );

    Registry.get(GradleExcludeBuildFilesDataService.REGISTRY_KEY).setValue(false);
    importPerSourceSet(true);
    importProject(buildScript.generate());
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project");
    assertContentRoots("project.main", getProjectPath());
    assertExcludes("project.main", "build");
    assertNoExcludePatterns("project.main", getExternalSystemConfigFileName());

    Registry.get(GradleExcludeBuildFilesDataService.REGISTRY_KEY).setValue(true);
    importPerSourceSet(true);
    importProject(buildScript.generate());
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project");
    assertContentRoots("project.main", getProjectPath());
    assertExcludes("project.main", "build");
    assertExcludePatterns("project.main", getExternalSystemConfigFileName());

    importPerSourceSet(false);
    importProject(buildScript.generate());
    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertExcludes("project", "build");
    assertExcludePatterns("project", getExternalSystemConfigFileName());
  }

  @Test
  @TargetVersions("7.4+")
  public void testJvmTestSuitesImportedInDelegatedMode() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(true);

    createProjectSubDir("src/main/java");
    createProjectSubDir("src/main/resources");
    createProjectSubDir("src/test/java");
    createProjectSubDir("src/test/resources");
    createProjectSubDir("src/integrationTest/java");
    createProjectSubDir("src/integrationTest/resources");

    importProject(script(it -> it
      .withJavaPlugin()
      .withPlugin("jvm-test-suite")
      .addPostfix(
        """
          testing {
              suites {
                  integrationTest(JvmTestSuite) {}
              }
          }
          """
      )
    ));

    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");
    assertGeneratedSources("project");
    assertGeneratedResources("project");
    assertGeneratedTestSources("project");
    assertGeneratedTestResources("project");

    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"));
    assertResources("project.main", path("src/main/resources"));
    assertTestSources("project.main");
    assertTestResources("project.main");
    assertGeneratedSources("project.main");
    assertGeneratedResources("project.main");
    assertGeneratedTestSources("project.main");
    assertGeneratedTestResources("project.main");
    assertModuleOutput("project.main", path("build/classes/java/main"), "");
    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));

    assertContentRoots("project.test", path("src/test"));
    assertSources("project.test");
    assertResources("project.test");
    assertTestSources("project.test", path("src/test/java"));
    assertTestResources("project.test", path("src/test/resources"));
    assertGeneratedSources("project.test");
    assertGeneratedResources("project.test");
    assertGeneratedTestSources("project.test");
    assertGeneratedTestResources("project.test");
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));

    assertContentRoots("project.integrationTest", path("src/integrationTest"));
    assertSources("project.integrationTest");
    assertResources("project.integrationTest");
    assertTestSources("project.integrationTest", path("src/integrationTest/java"));
    assertTestResources("project.integrationTest", path("src/integrationTest/resources"));
    assertGeneratedSources("project.integrationTest");
    assertGeneratedResources("project.integrationTest");
    assertGeneratedTestSources("project.integrationTest");
    assertGeneratedTestResources("project.integrationTest");
    assertModuleOutput("project.integrationTest", "", path("build/classes/java/integrationTest"));
    assertModuleOutputs("project.integrationTest", path("build/classes/java/integrationTest"), path("build/resources/integrationTest"));
  }

  @Test
  @TargetVersions("7.4+")
  public void testJvmTestSuitesImportedInNonDelegatedMode() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(false);

    createProjectSubDir("src/main/java");
    createProjectSubDir("src/main/resources");
    createProjectSubDir("src/test/java");
    createProjectSubDir("src/test/resources");
    createProjectSubDir("src/integrationTest/java");
    createProjectSubDir("src/integrationTest/resources");

    importProject(script(it -> it
      .withJavaPlugin()
      .withPlugin("jvm-test-suite")
      .addPostfix(
        """
          testing {
              suites {
                  integrationTest(JvmTestSuite) {}
              }
          }
          """
      )
    ));

    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertExcludes("project", ".gradle", "build", "out");

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");
    assertGeneratedSources("project");
    assertGeneratedResources("project");
    assertGeneratedTestSources("project");
    assertGeneratedTestResources("project");

    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"));
    assertResources("project.main", path("src/main/resources"));
    assertTestSources("project.main");
    assertTestResources("project.main");
    assertGeneratedSources("project.main");
    assertGeneratedResources("project.main");
    assertGeneratedTestSources("project.main");
    assertGeneratedTestResources("project.main");
    assertModuleOutput("project.main", path("out/production/classes"), "");
    assertModuleOutputs("project.main", path("out/production/classes"), path("out/production/resources"));

    assertContentRoots("project.test", path("src/test"));
    assertSources("project.test");
    assertResources("project.test");
    assertTestSources("project.test", path("src/test/java"));
    assertTestResources("project.test", path("src/test/resources"));
    assertGeneratedSources("project.test");
    assertGeneratedResources("project.test");
    assertGeneratedTestSources("project.test");
    assertGeneratedTestResources("project.test");
    assertModuleOutput("project.test", "", path("out/test/classes"));
    assertModuleOutputs("project.test", path("out/test/classes"), path("out/test/resources"));

    assertContentRoots("project.integrationTest", path("src/integrationTest"));
    assertSources("project.integrationTest");
    assertResources("project.integrationTest");
    assertTestSources("project.integrationTest", path("src/integrationTest/java"));
    assertTestResources("project.integrationTest", path("src/integrationTest/resources"));
    assertGeneratedSources("project.integrationTest");
    assertGeneratedResources("project.integrationTest");
    assertGeneratedTestSources("project.integrationTest");
    assertGeneratedTestResources("project.integrationTest");
    assertModuleOutput("project.integrationTest", "", path("out/integrationTest/classes"));
    assertModuleOutputs("project.integrationTest", path("out/integrationTest/classes"), path("out/integrationTest/resources"));
  }

  @Test
  @TargetVersions("5.6+")
  public void testJvmTestFixturesImportedInDelegatedMode() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(true);

    createProjectSubDir("src/main/java");
    createProjectSubDir("src/main/resources");
    createProjectSubDir("src/test/java");
    createProjectSubDir("src/test/resources");
    createProjectSubDir("src/testFixtures/java");
    createProjectSubDir("src/testFixtures/resources");

    importProject(script(it -> it
      .withJavaPlugin()
      .withPlugin("java-test-fixtures")
    ));

    assertModules("project", "project.main", "project.test", "project.testFixtures");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");
    assertGeneratedSources("project");
    assertGeneratedResources("project");
    assertGeneratedTestSources("project");
    assertGeneratedTestResources("project");

    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"));
    assertResources("project.main", path("src/main/resources"));
    assertTestSources("project.main");
    assertTestResources("project.main");
    assertGeneratedSources("project.main");
    assertGeneratedResources("project.main");
    assertGeneratedTestSources("project.main");
    assertGeneratedTestResources("project.main");
    assertModuleOutput("project.main", path("build/classes/java/main"), "");
    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));

    assertContentRoots("project.test", path("src/test"));
    assertSources("project.test");
    assertResources("project.test");
    assertTestSources("project.test", path("src/test/java"));
    assertTestResources("project.test", path("src/test/resources"));
    assertGeneratedSources("project.test");
    assertGeneratedResources("project.test");
    assertGeneratedTestSources("project.test");
    assertGeneratedTestResources("project.test");
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));

    assertContentRoots("project.testFixtures", path("src/testFixtures"));
    assertSources("project.testFixtures");
    assertResources("project.testFixtures");
    assertTestSources("project.testFixtures", path("src/testFixtures/java"));
    assertTestResources("project.testFixtures", path("src/testFixtures/resources"));
    assertGeneratedSources("project.testFixtures");
    assertGeneratedResources("project.testFixtures");
    assertGeneratedTestSources("project.testFixtures");
    assertGeneratedTestResources("project.testFixtures");
    assertModuleOutput("project.testFixtures", "", path("build/classes/java/testFixtures"));
    assertModuleOutputs("project.testFixtures", path("build/classes/java/testFixtures"), path("build/resources/testFixtures"));
  }

  @Test
  @TargetVersions("5.6+")
  public void testJvmTestFixturesImportedInNonDelegatedMode() throws Exception {
    getCurrentExternalProjectSettings().setDelegatedBuild(false);

    createProjectSubDir("src/main/java");
    createProjectSubDir("src/main/resources");
    createProjectSubDir("src/test/java");
    createProjectSubDir("src/test/resources");
    createProjectSubDir("src/testFixtures/java");
    createProjectSubDir("src/testFixtures/resources");

    importProject(script(it -> it
      .withJavaPlugin()
      .withPlugin("java-test-fixtures")
    ));

    assertModules("project", "project.main", "project.test", "project.testFixtures");

    assertExcludes("project", ".gradle", "build", "out");

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");
    assertGeneratedSources("project");
    assertGeneratedResources("project");
    assertGeneratedTestSources("project");
    assertGeneratedTestResources("project");

    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"));
    assertResources("project.main", path("src/main/resources"));
    assertTestSources("project.main");
    assertTestResources("project.main");
    assertGeneratedSources("project.main");
    assertGeneratedResources("project.main");
    assertGeneratedTestSources("project.main");
    assertGeneratedTestResources("project.main");
    assertModuleOutput("project.main", path("out/production/classes"), "");
    assertModuleOutputs("project.main", path("out/production/classes"), path("out/production/resources"));

    assertContentRoots("project.test", path("src/test"));
    assertSources("project.test");
    assertResources("project.test");
    assertTestSources("project.test", path("src/test/java"));
    assertTestResources("project.test", path("src/test/resources"));
    assertGeneratedSources("project.test");
    assertGeneratedResources("project.test");
    assertGeneratedTestSources("project.test");
    assertGeneratedTestResources("project.test");
    assertModuleOutput("project.test", "", path("out/test/classes"));
    assertModuleOutputs("project.test", path("out/test/classes"), path("out/test/resources"));

    assertContentRoots("project.testFixtures", path("src/testFixtures"));
    assertSources("project.testFixtures");
    assertResources("project.testFixtures");
    assertTestSources("project.testFixtures", path("src/testFixtures/java"));
    assertTestResources("project.testFixtures", path("src/testFixtures/resources"));
    assertGeneratedSources("project.testFixtures");
    assertGeneratedResources("project.testFixtures");
    assertGeneratedTestSources("project.testFixtures");
    assertGeneratedTestResources("project.testFixtures");
    assertModuleOutput("project.testFixtures", "", path("out/testFixtures/classes"));
    assertModuleOutputs("project.testFixtures", path("out/testFixtures/classes"), path("out/testFixtures/resources"));
  }

  protected void assertDefaultGradleJavaProjectFolders() {
    if (GradleProjectSettings.isDelegatedBuildEnabled(myProject, getProjectPath())) {
      assertExcludes("project", ".gradle", "build");
    }
    else {
      assertExcludes("project", ".gradle", "build", "out");
    }

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");

    assertContentRoots("project.main", path("src/main"));
    assertSources("project.main", path("src/main/java"));
    assertResources("project.main", path("src/main/resources"));

    assertContentRoots("project.test", path("src/test"));
    assertTestSources("project.test", path("src/test/java"));
    assertTestResources("project.test", path("src/test/resources"));
  }

  protected void assertDefaultGradleJavaProjectFoldersForMergedModule() {
    if (GradleProjectSettings.isDelegatedBuildEnabled(myProject, getProjectPath())) {
      assertExcludes("project", ".gradle", "build");
    }
    else {
      assertExcludes("project", ".gradle", "build", "out");
    }

    assertContentRoots("project", getProjectPath());
    assertSources("project", path("src/main/java"));
    assertResources("project", path("src/main/resources"));
    assertTestSources("project", path("src/test/java"));
    assertTestResources("project", path("src/test/resources"));
  }

  private void createDefaultDirs() throws IOException {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/java/A.java");
    createProjectSubFile("src/main/resources/resource.properties");
    createProjectSubFile("src/test/resources/test_resource.properties");
  }

  @Nullable
  protected ContentEntry findContentEntry(@NotNull String moduleName, @NotNull String contentPath) {
    ModuleRootManager moduleRootManager = getRootManager(moduleName);
    Module module = moduleRootManager.getModule();
    String rootPath = getAbsolutePath(ExternalSystemApiUtil.getExternalProjectPath(module));
    String expectedContentPath = getAbsolutePath(rootPath + "/" + contentPath);
    ContentEntry[] contentEntries = moduleRootManager.getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      String actualContentPath = getAbsolutePath(contentEntry.getUrl());
      if (actualContentPath.equals(expectedContentPath)) return contentEntry;
    }
    return null;
  }

  protected void assertContentEntryExists(@NotNull String moduleName, String @NotNull ... contentPaths) {
    for (String contentPath : contentPaths) {
      ContentEntry contentEntry = findContentEntry(moduleName, contentPath);
      assertNotNull("Content entry " + contentPath + " not found in module " + moduleName, contentEntry);
    }
  }

  protected void assertSourceExists(@NotNull String moduleName, String @NotNull ... sourcePaths) {
    for (String sourcePath : sourcePaths) {
      SourceFolder sourceFolder = findSource(moduleName, sourcePath);
      assertNotNull("Source folder " + sourcePath + " not found in module " + moduleName, sourceFolder);
    }
  }

  protected void importPerSourceSet(boolean b) {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(b);
  }

  protected void waitForModulesUpdate() throws Exception {
    edt(() -> {
      ((SourceFolderManagerImpl)SourceFolderManager.getInstance(myProject)).consumeBulkOperationsState(future -> {
        PlatformTestUtil.waitForFuture(future, 1000);
        return null;
      });
    });
  }
}
