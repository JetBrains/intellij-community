// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl;
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
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.service.project.data.GradleExcludeBuildFilesDataService;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction;

/**
 * @author Vladislav.Soroka
 */
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testUnsupportedTypesInDsl() throws Exception {
    importProject(
      """
        import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
        import org.gradle.internal.reflect.Instantiator;
        class MyObj implements Named {
          String myName;
          public MyObj(String name) {
            myName = namse
          }
        
          public String getName() {
            return myName
          }
        }
        project.extensions.create(
                        "sourceSets",
                        FactoryNamedDomainObjectContainer,
                        MyObj,
                        services.get(Instantiator),
                       {action -> }
                )
        sourceSets {
         println "Hello World!"
        }
        """
    );
  }

  @Test
  public void testImportBaseJavaProjectInNonDelegatedAndModulePerSourceSetModes() throws Exception {
    createDefaultDirs();
    createProjectSubFile("build.gradle", "apply plugin: 'java'");

    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();

    assertModules("project", "project.main", "project.test");

    assertNoExcludePatterns("project", "build.gradle");
    assertExcludes("project", ".gradle", "build", "out");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleOutputs("project.main", path("out/production/classes"), path("out/production/resources"));
    assertModuleOutput("project.main", path("out/production/classes"), "");

    assertModuleOutputs("project.test", path("out/test/classes"), path("out/test/resources"));
    assertModuleOutput("project.test", "", path("out/test/classes"));
  }

  @Test
  public void testImportBaseJavaProjectInNonDelegatedAndModulePerProjectModes() throws Exception {
    createDefaultDirs();
    createProjectSubFile("build.gradle", "apply plugin: 'java'");

    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build", "out");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleOutputs("project",
                        path("out/production/classes"),
                        path("out/production/resources"),
                        path("out/test/classes"),
                        path("out/test/resources"));
    assertModuleOutput("project",
                       path("out/production/classes"),
                       path("out/test/classes"));
  }

  @Test
  public void testImportBaseJavaProjectInDelegatedAndModulePerProjectModes() throws Exception {
    createDefaultDirs();
    createProjectSubFile("build.gradle", "apply plugin: 'java'");

    getCurrentExternalProjectSettings().setDelegatedBuild(true);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleOutputs("project",
                        path("build/classes/java/main"),
                        path("build/resources/main"),
                        path("build/classes/java/test"),
                        path("build/resources/test"));
    assertModuleOutput("project",
                       path("build/classes/java/main"),
                       path("build/classes/java/test"));
  }

  @Test
  public void testImportBaseJavaProjectInDelegatedAndModulePerSourceSetModes() throws Exception {
    createDefaultDirs();
    createProjectSubFile("build.gradle", "apply plugin: 'java'");

    getCurrentExternalProjectSettings().setDelegatedBuild(true);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();

    assertModules("project", "project.main", "project.test");

    assertNoExcludePatterns("project", "build.gradle");
    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));
    assertModuleOutput("project.main", path("build/classes/java/main"), "");

    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
  }

  @Test
  @TargetVersions("5.6+")
  public void testBaseJavaProjectHasNoWarnings() throws Exception {
    createDefaultDirs();
    createProjectSubFile("gradle.properties", "org.gradle.warning.mode=fail");
    importProject("apply plugin: 'java'");

    assertModules("project", "project.main", "project.test");

    assertNoExcludePatterns("project", "build.gradle");
    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));
    assertModuleOutput("project.main", path("build/classes/java/main"), "");

    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
  }

  @Test
  public void testCompileOutputPathCustomizedWithIdeaPlugin() throws Exception {
    createDefaultDirs();
    createProjectSubFile("build.gradle", """
      apply plugin: 'java'
      apply plugin: 'idea'
      idea {
        module {
          outputDir = file(buildDir)
        }
      }
      """);

    importProject();
    assertModules("project", "project.main", "project.test");
    assertModuleOutput("project.main", path("build/classes/java/main"), "");
    assertModuleOutput("project.test", "", path("build/classes/java/test"));


    getCurrentExternalProjectSettings().setDelegatedBuild(false);
    GradleSettings.getInstance(myProject).getPublisher().onBuildDelegationChange(false, getProjectPath());
    assertModules("project", "project.main", "project.test");
    assertModuleOutput("project.main", path("build"), "");
    assertModuleOutput("project.test", "", path("out/test/classes"));


    getCurrentExternalProjectSettings().setDelegatedBuild(true);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();
    assertModules("project");
    assertModuleOutput("project", path("build"), path("build/classes/java/test"));
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

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
  }

  @Test
  public void testCustomSourceSetsAreImported() throws Exception {
    createDefaultDirs();
    createProjectSubFile("src/customSourceSet/java/G.java");

    importProject("""
                    apply plugin: 'java'
                    apply plugin: 'idea'
                    
                    sourceSets {
                      customSourceSet
                    }""");

    assertModules("project", "project.main", "project.test", "project.customSourceSet");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertContentRoots("project.customSourceSet", path("src/customSourceSet"));
    assertSourceRoots("project.customSourceSet", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/customSourceSet/java"))
    );


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"), path("src/customSourceSet/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
  }

  @Test
  @TargetVersions("4.7+")
  public void testResourceFoldersWithIdeaPluginInNonJavaProject() throws Exception {
    createProjectSubDirs("python/src", "python/test", "python/resources");
    importProject(script(it -> it
      .withIdeaPlugin()
      .addPostfix(
        """
          idea {
            module {
              sourceDirs += file('python/src')
              resourceDirs += file('python/resources')
              testSourceDirs += file('python/test')
            }
          }
          """
      )
    ));

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("python/src"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("python/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("python/test"))
    );
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

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"), path("src/main/src2"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"), path("src/main/resources2"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"), path("src/test/src2"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"), path("src/test/resources2"))
    );


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"), path("src/main/src2"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"), path("src/main/resources2"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"), path("src/test/src2"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"), path("src/test/resources2"))
    );
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

      assertExcludes("project", ".gradle", "build");

      assertContentRoots("project", getProjectPath());
      assertNoSourceRoots("project");

      assertContentRoots("project.main", path("src/main"));
      assertSourceRoots("project.main", it -> it
        .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
        .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      );

      assertContentRoots("project.test", path("src/test"));
      assertSourceRoots("project.test", it -> it
        .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"), path("src/test/src2"))
        .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"), path("src/test/resources2"))
      );

      assertContentRoots("project.customSourceSet", path("src/customSourceSet"));
      assertSourceRoots("project.customSourceSet", it -> it
        .sourceRoots(ExternalSystemSourceType.TEST, path("src/customSourceSet/java"))
        .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/customSourceSet/resources"))
      );
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

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

    assertModuleInheritedOutput("project");
    assertModuleInheritedOutput("project.main");
    assertModuleInheritedOutput("project.test");


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );

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

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src"), path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/resources"), path("src/main/resources"))
    );

    assertContentRoots("project.test", path("test"), path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("test"), path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("test/resources"), path("src/test/resources"))
    );


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src"), path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/resources"), path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("test"), path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("test/resources"), path("src/test/resources"))
    );
  }

  @Test
  public void testRootsAreNotCreatedIfFilesAreMissing() throws Exception {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/resources/res.properties");

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        sourceSets.main.java.srcDirs file('src/generated/java')
        idea.module {
          generatedSourceDirs += file('src/generated/java')
        }
        """
    );

    assertModules("project");

    assertExcludes("project", ".gradle", "build");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
  }

  @Test
  public void testRootsAreAddedWhenAFolderCreated() throws Exception {
    createProjectSubFile("src/main/java/A.java");

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        sourceSets.main.java.srcDirs file('src/generated/java')
        idea.module {
          generatedSourceDirs += file('src/generated/java')
        }
        """
    );

    assertModules("project");
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
    );

    createProjectSubFile("src/test/java/ATest.java");
    waitForModulesUpdate();
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
    );

    createProjectSubFile("src/main/resources/res.txt");
    waitForModulesUpdate();
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
    );

    createProjectSubFile("src/generated/java/Generated.java");
    waitForModulesUpdate();
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
      .sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path("src/generated/java"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
    );
  }

  @Test
  public void testRootsListenersAreUpdatedWithProjectModel() throws Exception {
    createProjectSubFile("src/main/java/A.java");

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject("apply plugin: 'java'");

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
    );

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(
      """
        apply plugin: 'java'
        
        sourceSets {
          test {
            java.srcDirs = [file('test-src/java')]
          }
        }
        """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
    );

    createProjectSubFile("src/test/java/ATest.java");
    createProjectSubFile("test-src/java/BTest.java");
    waitForModulesUpdate();
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("test-src/java"))
    );
  }


  @Test
  public void testSourceAndResourceFoldersCollision() throws Exception {
    createProjectSubFile("src/A.java");
    createProjectSubFile("src/production.properties");
    createProjectSubFile("test/Test.java");
    createProjectSubFile("test/test.properties");

    importProject(
      """
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
        """
    );

    assertModules("project", "project.main", "project.test");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src"))
    );

    assertContentRoots("project.test", path("test"), path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("test"))
    );


    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src"))
      .sourceRoots(ExternalSystemSourceType.TEST, path("test"))
    );
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
  public void testSourceFoldersOutOfContentRoot() throws Exception {
    createProjectSubFile("src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer1/src/main/kotlin/A.kt", "class A {}");
    createProjectSubFile("../outer2/src/main/java/A.java", "class A {}");
    createProjectSubFile("../outer3/A.java", "class A {}");
    createProjectSubFile("build/generated/A.java", "class A {}");
    createProjectSubFile("../outer4/generated/A.java", "class A {}");
    createProjectSubFile("build.gradle", script(it -> it
      .withJavaPlugin()
      .withIdeaPlugin()
      .addPrefix(
        """
          sourceSets {
            generated.java.srcDirs += "${buildDir}/generated"
            generated.java.srcDirs += '../outer4/generated'
            main.java.srcDirs += '../outer1/src/main/java'
            main.java.srcDirs += '../outer1/src/main/kotlin'
            main.java.srcDirs += '../outer2/src/main/java'
            main.java.srcDirs += '../outer3'
          }
          """
      ).addPrefix(
        """
          idea {
            module {
              inheritOutputDirs = true
              generatedSourceDirs += file("${buildDir}/generated")
              generatedSourceDirs += file('../outer4/generated')
            }
          }
          """
      )
    ));

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();

    assertModules("project", "project.main", "project.test", "project.generated");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.main",
                       path("src/main"),
                       path("../outer1/src/main"),
                       path("../outer2/src/main"),
                       path("../outer3")
    );
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE,
                   path("src/main/java"),
                   path("../outer1/src/main/java"),
                   path("../outer1/src/main/kotlin"),
                   path("../outer2/src/main/java"),
                   path("../outer3")
      )
    );

    assertContentRoots("project.test", path("src/test"));
    assertNoSourceRoots("project.test");

    assertContentRoots("project.generated",
                       path("src/generated"),
                       path("build/generated"),
                       path("../outer4")
    );
    assertSourceRoots("project.generated", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED,
                   path("build/generated"),
                   path("../outer4/generated")
      )
    );

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project");

    assertContentRoots("project",
                       getProjectPath(),
                       path("../outer1/src/main/java"),
                       path("../outer1/src/main/kotlin"),
                       path("../outer2/src/main/java"),
                       path("../outer3"),
                       path("../outer4/generated")
    );
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE,
                   path("src/main/java"),
                   path("../outer1/src/main/java"),
                   path("../outer1/src/main/kotlin"),
                   path("../outer2/src/main/java"),
                   path("../outer3")
      )
      .sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED,
                   path("build/generated"),
                   path("../outer4/generated")
      )
    );
  }

  @Test
  public void testMultipleSourcesConsistencyCompilerOutput() throws Exception {
    createProjectSubFile("src/main/java/A.java", "class A {}");
    createProjectSubFile("src/main/kotlin/A.kt", "class A {}");

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(script(it -> it
      .withMavenCentral()
      .withKotlinJvmPlugin()
      .withJavaLibraryPlugin()
    ));

    assertModules("project");

    assertContentRoots("project", getProjectPath());
    assertSourceRoots("project", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"), path("src/main/kotlin"))
    );

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
        }
        """
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
    createProjectSubFile("app1/build.gradle", script(it -> it
      .withJavaPlugin()
      .addPostfix(
        """
          sourceSets {
            main.resources.srcDir '../shared/resources'
          }
          """
      )
    ));
    createProjectSubFile("app2/build.gradle", script(it -> it
      .withJavaPlugin()
      .addPostfix(
        """
          sourceSets {
            main.resources.srcDir '../shared/resources'
          }
          """
      )
    ));

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();

    assertModules("project", "project.app1", "project.app2");

    assertContentRoots("project", getProjectPath());
    assertNoSourceRoots("project");

    assertContentRoots("project.app1", path("app1"), path("shared/resources"));
    assertSourceRoots("project.app1", it -> it
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("shared/resources"))
    );

    assertContentRoots("project.app2", path("app2"));
    assertNoSourceRoots("project.app2");
  }

  @Test
  public void testBuildFileAtSourceRootLayout() throws Exception {
    createProjectSubDir("build");
    createProjectSubFile("build.gradle", script(it -> it
      .withJavaPlugin()
      .addPostfix(
        """
          sourceSets {
            main.java.srcDirs = ['.']
          }
          """
      )
    ));

    Registry.get(GradleExcludeBuildFilesDataService.REGISTRY_KEY).setValue(false);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project");
    assertContentRoots("project.main", getProjectPath());
    assertExcludes("project.main", "build");
    assertNoExcludePatterns("project.main", getExternalSystemConfigFileName());

    Registry.get(GradleExcludeBuildFilesDataService.REGISTRY_KEY).setValue(true);
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(true);
    importProject();
    assertModules("project", "project.main", "project.test");
    assertContentRoots("project");
    assertContentRoots("project.main", getProjectPath());
    assertExcludes("project.main", "build");
    assertExcludePatterns("project.main", getExternalSystemConfigFileName());

    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();
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
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertModuleOutput("project.main", path("build/classes/java/main"), "");
    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));

    assertContentRoots("project.integrationTest", path("src/integrationTest"));
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/integrationTest/resources"))
    );
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
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertModuleOutput("project.main", path("out/production/classes"), "");
    assertModuleOutputs("project.main", path("out/production/classes"), path("out/production/resources"));

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertModuleOutput("project.test", "", path("out/test/classes"));
    assertModuleOutputs("project.test", path("out/test/classes"), path("out/test/resources"));

    assertContentRoots("project.integrationTest", path("src/integrationTest"));
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/integrationTest/resources"))
    );
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
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertModuleOutput("project.main", path("build/classes/java/main"), "");
    assertModuleOutputs("project.main", path("build/classes/java/main"), path("build/resources/main"));

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertModuleOutput("project.test", "", path("build/classes/java/test"));
    assertModuleOutputs("project.test", path("build/classes/java/test"), path("build/resources/test"));

    assertContentRoots("project.testFixtures", path("src/testFixtures"));
    assertSourceRoots("project.testFixtures", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/testFixtures/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/testFixtures/resources"))
    );
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
    assertNoSourceRoots("project");

    assertContentRoots("project.main", path("src/main"));
    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertModuleOutput("project.main", path("out/production/classes"), "");
    assertModuleOutputs("project.main", path("out/production/classes"), path("out/production/resources"));

    assertContentRoots("project.test", path("src/test"));
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertModuleOutput("project.test", "", path("out/test/classes"));
    assertModuleOutputs("project.test", path("out/test/classes"), path("out/test/resources"));

    assertContentRoots("project.testFixtures", path("src/testFixtures"));
    assertSourceRoots("project.testFixtures", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/testFixtures/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/testFixtures/resources"))
    );
    assertModuleOutput("project.testFixtures", "", path("out/testFixtures/classes"));
    assertModuleOutputs("project.testFixtures", path("out/testFixtures/classes"), path("out/testFixtures/resources"));
  }

  private void createDefaultDirs() throws IOException {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/java/A.java");
    createProjectSubFile("src/main/resources/resource.properties");
    createProjectSubFile("src/test/resources/test_resource.properties");
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
