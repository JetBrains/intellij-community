// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.JavaApplicationRunConfigurationImporter;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.jar.JarApplicationRunConfigurationImporter;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl;
import com.intellij.openapi.externalSystem.service.project.settings.FacetConfigurationImporter;
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.PathUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.extPluginVersionIsAtLeast;

/**
 * Created by Nikita.Skvortsov
 */
public class GradleSettingsImportingTest extends GradleSettingsImportingTestCase {
  @Test
  public void testInspectionSettingsImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              inspections {
                myInspection { enabled = false }
              }
            }
          }""")
    );

    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(getMyProject()).getCurrentProfile();
    assertEquals("Gradle Imported", profile.getName());
  }

  @Test
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    TestRunConfigurationImporter testExtension = new TestRunConfigurationImporter("application");
    maskRunImporter(testExtension);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              runConfigurations {
                 app1(Application) {
                     mainClass = 'my.app.Class'
                     jvmArgs =   '-Xmx1g'
                     moduleName = 'moduleName'
                 }
                 app2(Application) {
                     mainClass = 'my.app.Class2'
                     moduleName = 'moduleName'
                 }
              }
            }
          }""")
    );

    final Map<String, Map<String, Object>> configs = testExtension.getConfigs();

    assertContain(new ArrayList<>(configs.keySet()), "app1", "app2");
    Map<String, Object> app1Settings = configs.get("app1");
    Map<String, Object> app2Settings = configs.get("app2");

    assertEquals("my.app.Class", app1Settings.get("mainClass"));
    assertEquals("my.app.Class2", app2Settings.get("mainClass"));
    assertEquals("-Xmx1g", app1Settings.get("jvmArgs"));
    assertNull(app2Settings.get("jvmArgs"));
  }

  @Test
  public void testGradleRunConfigurationSettingsImport() throws Exception {
    TestRunConfigurationImporter testExtension = new TestRunConfigurationImporter("gradle");
    maskRunImporter(testExtension);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPluginIfCan()
        .addPostfix(
          "import org.jetbrains.gradle.ext.*",
          "idea.project.settings {",
          "  runConfigurations {",
          "    gr1(Gradle) {",
          "      project = rootProject",
          "      taskNames = [':cleanTest', ':test']",
          "      envs = ['env_key':'env_val']",
          "      jvmArgs = '-DvmKey=vmVal'",
          "      scriptParameters = '-PscriptParam'",
          "    }",
          "  }",
          "}"
        ).generate());


    final Map<String, Map<String, Object>> configs = testExtension.getConfigs();

    assertContain(new ArrayList<>(configs.keySet()), "gr1");
    Map<String, Object> gradleSettings = configs.get("gr1");

    assertEquals(myProjectRoot.getPath(), ((String)gradleSettings.get("projectPath")).replace('\\', '/'));
    assertTrue(((List<?>)gradleSettings.get("taskNames")).contains(":cleanTest"));
    assertEquals("-DvmKey=vmVal", gradleSettings.get("jvmArgs"));
    assertTrue(((Map<?, ?>)gradleSettings.get("envs")).containsKey("env_key"));
  }

  private void maskRunImporter(@NotNull RunConfigurationImporter testExtension) {
    ExtensionTestUtil.maskExtensions(RunConfigurationImporter.EP_NAME, Collections.singletonList(testExtension), getTestRootDisposable());
  }

  @Test
  public void testDefaultRCSettingsImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    maskRunImporter(appcConfigImporter);

    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              runConfigurations {
                 defaults(Application) {
                     jvmArgs = '-DmyKey=myVal'
                 }
              }
            }
          }""")
    );

    final RunManager runManager = RunManager.getInstance(getMyProject());
    final RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(appcConfigImporter.getConfigurationFactory());
    final String parameters = ((ApplicationConfiguration)template.getConfiguration()).getVMParameters();

    assertNotNull(parameters);
    assertTrue(parameters.contains("-DmyKey=myVal"));
  }

  @Test
  public void testDefaultsAreUsedDuringImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    maskRunImporter(appcConfigImporter);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              runConfigurations {
                 defaults(Application) {
                     jvmArgs = '-DmyKey=myVal'
                 }
                 'My Run'(Application) {
                     mainClass = 'my.app.Class'
                     moduleName = 'moduleName'
                 }
              }
            }
          }""")
    );

    final RunManager runManager = RunManager.getInstance(getMyProject());
    final RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(appcConfigImporter.getConfigurationFactory());
    final String parameters = ((ApplicationConfiguration)template.getConfiguration()).getVMParameters();

    assertNotNull(parameters);
    assertTrue(parameters.contains("-DmyKey=myVal"));

    final ApplicationConfiguration myRun = (ApplicationConfiguration)runManager.findConfigurationByName("My Run").getConfiguration();
    assertNotNull(myRun);
    final String actualParams = myRun.getVMParameters();
    assertNotNull(actualParams);
    assertTrue(actualParams.contains("-DmyKey=myVal"));
    assertEquals("my.app.Class", myRun.getMainClassName());
  }

  @Test
  public void testBeforeRunTaskImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    maskRunImporter(appcConfigImporter);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              runConfigurations {
                 'My Run'(Application) {
                     mainClass = 'my.app.Class'
                     moduleName = 'moduleName'
                     beforeRun {
                         gradle(GradleTask) { task = tasks['projects'] }
                     }
                 }
              }
            }
          }""")
    );

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(getMyProject());
    final ApplicationConfiguration myRun = (ApplicationConfiguration)runManager.findConfigurationByName("My Run").getConfiguration();
    assertNotNull(myRun);

    final List<BeforeRunTask> tasks = runManager.getBeforeRunTasks(myRun);
    assertSize(2, tasks);
    final BeforeRunTask gradleBeforeRunTask = tasks.get(1);
    assertInstanceOf(gradleBeforeRunTask, ExternalSystemBeforeRunTask.class);
    final ExternalSystemTaskExecutionSettings settings = ((ExternalSystemBeforeRunTask)gradleBeforeRunTask).getTaskExecutionSettings();
    assertContain(settings.getTaskNames(), "projects");
    assertEquals(FileUtil.toSystemIndependentName(getProjectPath()),
                 FileUtil.toSystemIndependentName(settings.getExternalProjectPath()));
  }

  @Test
  public void testJarApplicationRunConfigurationSettingsImport() throws Exception {
    TestRunConfigurationImporter testExtension = new TestRunConfigurationImporter("jarApplication");
    maskRunImporter(testExtension);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      createBuildScriptBuilder()
      .withGradleIdeaExtPluginIfCan()
      .addPostfix(
        "import org.jetbrains.gradle.ext.*",
        "idea.project.settings {",
        "  runConfigurations {",
        "    jarApp1(JarApplication) {",
        "      jarPath =    'my/app.jar'",
        "      jvmArgs =    '-DvmKey=vmVal'",
        "      moduleName = 'moduleName'",
        "    }",
        "    jarApp2(JarApplication) {",
        "      jarPath =    'my/app2.jar'",
        "      moduleName = 'moduleName'",
        "    }",
        "  }",
        "}"
      ).generate());

    final Map<String, Map<String, Object>> configs = testExtension.getConfigs();

    assertContain(new ArrayList<>(configs.keySet()), "jarApp1", "jarApp2");
    Map<String, Object> jarApp1Settings = configs.get("jarApp1");
    Map<String, Object> jarApp2Settings = configs.get("jarApp2");

    assertEquals("my/app.jar", jarApp1Settings.get("jarPath"));
    assertEquals("my/app2.jar", jarApp2Settings.get("jarPath"));
    assertEquals("-DvmKey=vmVal", jarApp1Settings.get("jvmArgs"));
    assertNull(jarApp2Settings.get("jvmArgs"));
  }

  @Test
  public void testJarApplicationBeforeRunGradleTaskImport() throws Exception {
    RunConfigurationImporter jarAppConfigImporter = new JarApplicationRunConfigurationImporter();
    maskRunImporter(jarAppConfigImporter);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea.project.settings {
            runConfigurations {
              'jarApp'(JarApplication) {
                beforeRun {
                  'gradleTask'(GradleTask) {
                    task = tasks['projects']
                  }
                }
              }
            }
          }"""
      )
    );

    RunManagerEx runManager = RunManagerEx.getInstanceEx(getMyProject());
    RunConfiguration jarApp = runManager.findConfigurationByName("jarApp").getConfiguration();
    assertNotNull(jarApp);

    List<BeforeRunTask> tasks = runManager.getBeforeRunTasks(jarApp);
    assertSize(1, tasks);
    BeforeRunTask gradleBeforeRunTask = tasks.get(0);
    assertInstanceOf(gradleBeforeRunTask, ExternalSystemBeforeRunTask.class);
    ExternalSystemTaskExecutionSettings settings = ((ExternalSystemBeforeRunTask)gradleBeforeRunTask).getTaskExecutionSettings();
    assertContain(settings.getTaskNames(), "projects");
    assertEquals(FileUtil.toSystemIndependentName(getProjectPath()),
                 FileUtil.toSystemIndependentName(settings.getExternalProjectPath()));
  }

  @Test
  public void testFacetSettingsImport() throws Exception {
    TestFacetConfigurationImporter testExtension = new TestFacetConfigurationImporter("spring");
    ExtensionTestUtil
      .maskExtensions(FacetConfigurationImporter.EP_NAME, Collections.<FacetConfigurationImporter>singletonList(testExtension),
                      getTestRootDisposable());
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            module.settings {
              facets {
                 spring(SpringFacet) {
                   contexts {
                      myParent {
                        file = 'parent_ctx.xml'
                      }
                      myChild {
                        file = 'child_ctx.xml'
                        parent = 'myParent'            }
                   }
                 }
              }
            }
          }""")
    );

    final Map<String, Map<String, Object>> facetConfigs = testExtension.getConfigs();

    assertContain(new ArrayList<>(facetConfigs.keySet()), "spring");
    List<Map<String, Object>> springCtxConfigs = (List<Map<String, Object>>)facetConfigs.get("spring").get("contexts");

    assertContain(springCtxConfigs.stream().map((Map m) -> m.get("name")).collect(Collectors.toList()), "myParent", "myChild");

    Map<String, Object> parentSettings = springCtxConfigs.stream()
      .filter((Map m) -> m.get("name").equals("myParent"))
      .findFirst()
      .get();
    Map<String, Object> childSettings = springCtxConfigs.stream()
      .filter((Map m) -> m.get("name").equals("myChild"))
      .findFirst()
      .get();

    assertEquals("parent_ctx.xml", parentSettings.get("file"));
    assertEquals("child_ctx.xml", childSettings.get("file"));
    assertEquals("myParent", childSettings.get("parent"));
  }

  @Test
  public void testTaskTriggersImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          idea {
            project.settings {
              taskTriggers {
                beforeSync tasks.getByName('projects'), tasks.getByName('tasks')
              }
            }
          }""")
    );

    final List<ExternalProjectsManagerImpl.ExternalProjectsStateProvider.TasksActivation> activations =
      ExternalProjectsManagerImpl.getInstance(getMyProject()).getStateProvider().getAllTasksActivation();

    assertSize(1, activations);

    final ExternalProjectsManagerImpl.ExternalProjectsStateProvider.TasksActivation activation = activations.get(0);
    assertEquals(GradleSettings.getInstance(getMyProject()).getLinkedProjectsSettings().iterator().next().getExternalProjectPath(),
                 activation.projectPath);
    final List<String> beforeSyncTasks = activation.state.getTasks(ExternalSystemTaskActivator.Phase.BEFORE_SYNC);

    if (extPluginVersionIsAtLeast("0.5")) {
      assertContain(beforeSyncTasks, "projects", "tasks");
    }
    else {
      assertContain(beforeSyncTasks, ":projects", ":tasks");
    }
  }

  @Test
  public void testIdeaPostProcessingHook() throws Exception {
    File layoutFile = new File(getProjectPath(), "test_output.txt");
    assertThat(layoutFile).doesNotExist();

    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .addPostfix("""
                      import org.jetbrains.gradle.ext.*
                      idea {
                        project.settings {
                          withIDEADir { File dir ->
                              def f = file("test_output.txt")
                              f.createNewFile()
                              f.text = "Expected file content"
                          } \s
                        }
                      }""")
        .generate()
    );
    final List<ExternalProjectsManagerImpl.ExternalProjectsStateProvider.TasksActivation> activations =
      ExternalProjectsManagerImpl.getInstance(getMyProject()).getStateProvider().getAllTasksActivation();

    assertThat(activations)
      .extracting("projectPath")
      .containsExactly(GradleSettings.getInstance(getMyProject()).getLinkedProjectsSettings().iterator().next().getExternalProjectPath());

    final List<String> afterSyncTasks = activations.get(0).state.getTasks(ExternalSystemTaskActivator.Phase.AFTER_SYNC);

    assertThat(afterSyncTasks).containsExactly("processIdeaSettings");

    String ideaDir = PathUtil.toSystemIndependentName(((ProjectStoreOwner)getMyProject()).getComponentStore()
      .getProjectFilePath().getParent().toAbsolutePath().toString());

    String moduleFile = getModule("project").getModuleFilePath();
    assertThat(layoutFile)
      .exists()
      .hasContent("Expected file content");
  }

  @Test
  public void testImportEncodingSettings() throws IOException {
    {
      importProject(
        createBuildScriptBuilder()
          .withGradleIdeaExtPlugin()
          .addImport("org.jetbrains.gradle.ext.EncodingConfiguration.BomPolicy")
          .addPostfix(
            "idea {",
            "  project {",
            "    settings {",
            "      encodings {",
            "        encoding = 'IBM-Thai'",
            "        bomPolicy = BomPolicy.WITH_NO_BOM",
            "        properties {",
            "          encoding = 'GB2312'",
            "          transparentNativeToAsciiConversion = true",
            "        }",
            "      }",
            "    }",
            "  }",
            "}")
          .generate());
      EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getMyProject());
      assertEquals("IBM-Thai", encodingManager.getDefaultCharset().name());
      assertEquals("GB2312", encodingManager.getDefaultCharsetForPropertiesFiles(null).name());
      assertTrue(encodingManager.isNative2AsciiForPropertiesFiles());
      assertFalse(encodingManager.shouldAddBOMForNewUtf8File());
    }
    {
      importProject(
        createBuildScriptBuilder()
          .withGradleIdeaExtPlugin()
          .addImport("org.jetbrains.gradle.ext.EncodingConfiguration.BomPolicy")
          .addPostfix(
            "idea {",
            "  project {",
            "    settings {",
            "      encodings {",
            "        encoding = 'UTF-8'",
            "        bomPolicy = BomPolicy.WITH_BOM",
            "        properties {",
            "          encoding = 'UTF-8'",
            "          transparentNativeToAsciiConversion = false",
            "        }",
            "      }",
            "    }",
            "  }",
            "}")
          .generate());
      EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getMyProject());
      assertEquals("UTF-8", encodingManager.getDefaultCharset().name());
      assertEquals("UTF-8", encodingManager.getDefaultCharsetForPropertiesFiles(null).name());
      assertFalse(encodingManager.isNative2AsciiForPropertiesFiles());
      assertTrue(encodingManager.shouldAddBOMForNewUtf8File());
    }
  }

  @Test
  public void testImportFileEncodingSettings() throws IOException {
    VirtualFile aDir = createProjectSubDir("src/main/java/a");
    VirtualFile bDir = createProjectSubDir("src/main/java/b");
    VirtualFile cDir = createProjectSubDir("src/main/java/c");
    VirtualFile mainDir = createProjectSubDir("../sub-project/src/main/java");
    createProjectSubFile("src/main/java/a/A.java");
    createProjectSubFile("src/main/java/c/C.java");
    createProjectSubFile("../sub-project/src/main/java/Main.java");
    {
      importProject(
        createBuildScriptBuilder()
          .withJavaPlugin()
          .withGradleIdeaExtPlugin()
          .addImport("org.jetbrains.gradle.ext.EncodingConfiguration.BomPolicy")
          .addPostfix(
            "sourceSets {",
            "  main.java.srcDirs += '../sub-project/src/main/java'",
            "}",
            "idea {",
            "  project {",
            "    settings {",
            "      encodings {",
            "        mapping['src/main/java/a'] = 'ISO-8859-9'",
            "        mapping['src/main/java/b'] = 'x-EUC-TW'",
            "        mapping['src/main/java/c'] = 'UTF-8'",
            "        mapping['../sub-project/src/main/java'] = 'KOI8-R'",
            "      }",
            "    }",
            "  }",
            "}")
          .generate());
      EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getMyProject());
      Map<String, String> allMappings = encodingManager.getAllMappings().entrySet().stream()
        .collect(Collectors.toMap(it -> it.getKey().getCanonicalPath(), it -> it.getValue().name()));
      assertEquals("ISO-8859-9", allMappings.get(aDir.getCanonicalPath()));
      assertEquals("x-EUC-TW", allMappings.get(bDir.getCanonicalPath()));
      assertEquals("UTF-8", allMappings.get(cDir.getCanonicalPath()));
      assertEquals("KOI8-R", allMappings.get(mainDir.getCanonicalPath()));
    }
    {
      importProject(
        createBuildScriptBuilder()
          .withJavaPlugin()
          .withGradleIdeaExtPlugin()
          .addImport("org.jetbrains.gradle.ext.EncodingConfiguration.BomPolicy")
          .addPostfix(
            "sourceSets {",
            "  main.java.srcDirs += '../sub-project/src/main/java'",
            "}",
            "idea {",
            "  project {",
            "    settings {",
            "      encodings {",
            "        mapping['src/main/java/a'] = '<System Default>'",
            "        mapping['src/main/java/b'] = '<System Default>'",
            "        mapping['../sub-project/src/main/java'] = '<System Default>'",
            "      }",
            "    }",
            "  }",
            "}")
          .generate());
      EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getMyProject());
      Map<String, String> allMappings = encodingManager.getAllMappings().entrySet().stream()
        .collect(Collectors.toMap(it -> it.getKey().getCanonicalPath(), it -> it.getValue().name()));
      assertNull(allMappings.get(aDir.getCanonicalPath()));
      assertNull(allMappings.get(bDir.getCanonicalPath()));
      assertEquals("UTF-8", allMappings.get(cDir.getCanonicalPath()));
      assertNull(allMappings.get(mainDir.getCanonicalPath()));
    }
  }

  @Test
  public void testActionDelegationImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        """
          import org.jetbrains.gradle.ext.*
          import static org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.*
          idea {
            project.settings {
              delegateActions {
                delegateBuildRunToGradle = true
                testRunner = CHOOSE_PER_TEST
              }
            }
          }""")
    );

    String projectPath = getCurrentExternalProjectSettings().getExternalProjectPath();
    assertTrue(GradleProjectSettings.isDelegatedBuildEnabled(getMyProject(), projectPath));
    assertEquals(TestRunner.CHOOSE_PER_TEST, GradleProjectSettings.getTestRunner(getMyProject(), projectPath));
  }

  @Test
  public void testSavePackagePrefixAfterReOpenProject() throws IOException {
    @Language("Groovy") String buildScript = createBuildScriptBuilder().withJavaPlugin().generate();
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(buildScript);
    Application application = ApplicationManager.getApplication();
    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getMyProject());
    try {
      Module module = modelsProvider.findIdeModule("project.main");
      ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
      SourceFolder sourceFolder = findSource(modifiableRootModel, "src/main/java");
      sourceFolder.setPackagePrefix("prefix.package.some");
      application.invokeAndWait(() -> application.runWriteAction(() -> modelsProvider.commit()));
    }
    finally {
      application.invokeAndWait(() -> modelsProvider.dispose());
    }
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    importProject(buildScript);
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
  }

  @Test
  public void testRemovingSourceFolderManagerMemLeaking() throws IOException {
    SourceFolderManagerImpl sourceFolderManager = (SourceFolderManagerImpl)SourceFolderManager.getInstance(getMyProject());
    String javaSourcePath = FileUtil.toCanonicalPath(myProjectRoot.getPath() + "/java");
    String javaSourceUrl = VfsUtilCore.pathToUrl(javaSourcePath);
    {
      importProject(
        createBuildScriptBuilder()
          .withJavaPlugin()
          .addPostfix(
            "sourceSets {",
            "  main.java.srcDirs += 'java'",
            "}")
          .generate());
      Set<String> sourceFolders = sourceFolderManager.getSourceFolders("project.main");
      assertTrue(sourceFolders.contains(javaSourceUrl));
    }
    {
      importProject(
        createBuildScriptBuilder()
          .withJavaPlugin()
          .generate());
      Set<String> sourceFolders = sourceFolderManager.getSourceFolders("project.main");
      assertFalse(sourceFolders.contains(javaSourceUrl));
    }
  }

  @Test
  public void testSourceFolderIsDisposedAfterProjectDisposing() throws IOException {
    importProject(createBuildScriptBuilder().generate());
    Application application = ApplicationManager.getApplication();
    Ref<Project> projectRef = new Ref<>();
    application.invokeAndWait(() -> projectRef.set(ProjectUtil.openOrImport(myProjectRoot.toNioPath())));
    Project project = projectRef.get();
    SourceFolderManagerImpl sourceFolderManager = (SourceFolderManagerImpl)SourceFolderManager.getInstance(project);
    try {
      assertFalse(project.isDisposed());
      assertFalse(sourceFolderManager.isDisposed());
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
    }
    assertTrue(project.isDisposed());
    assertTrue(sourceFolderManager.isDisposed());
  }

  @Test
  public void testPostponedImportPackagePrefix() throws Exception {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .withKotlinJvmPlugin()
        .addPostfix(
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/main/java'] = 'prefix.package.some'",
          "      packagePrefix['src/main/kotlin'] = 'prefix.package.other'",
          "      packagePrefix['src/test/java'] = 'prefix.package.some.test'",
          "    }",
          "  }",
          "}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourceNotExists("project.main", "src/main/kotlin");
    assertSourceNotExists("project.test", "src/test/java");
    createProjectSubFile("src/main/kotlin/Main.kt", "");
    edt(() -> {
      ((SourceFolderManagerImpl)SourceFolderManager.getInstance(getMyProject())).consumeBulkOperationsState(future -> {
        PlatformTestUtil.waitForFuture(future, 1000);
        return null;
      });
    });
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourcePackagePrefix("project.main", "src/main/kotlin", "prefix.package.other");
    assertSourceNotExists("project.test", "src/test/java");
  }

  @Test
  public void testPartialImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    createProjectSubFile("src/main/kotlin/Main.kt", "");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .withKotlinJvmPlugin()
        .addPostfix(
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/main/java'] = 'prefix.package.some'",
          "    }",
          "  }",
          "}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourcePackagePrefix("project.main", "src/main/kotlin", "");
  }

  @Test
  public void testImportPackagePrefixWithRemoteSourceRoot() throws IOException {
    createProjectSubFile("src/test/java/Main.java", "");
    createProjectSubFile("../subproject/src/test/java/Main.java", "");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .addPostfix(
          "sourceSets {",
          "  test.java.srcDirs += '../subproject/src/test/java'",
          "}",
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/test/java'] = 'prefix.package.some'",
          "      packagePrefix['../subproject/src/test/java'] = 'prefix.package.other'",
          "    }",
          "  }",
          "}")
        .generate());
    printProjectStructure();
    assertSourcePackagePrefix("project.test", "src/test/java", "prefix.package.some");
    assertSourcePackagePrefix("project.test", "../subproject/src/test/java", "prefix.package.other");
  }

  @Test
  public void testImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .addPostfix(
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/main/java'] = 'prefix.package.some'",
          "    }",
          "  }",
          "}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
  }

  @Test
  public void testChangeImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .addPostfix(
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/main/java'] = 'prefix.package.some'",
          "    }",
          "  }",
          "}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPlugin()
        .withJavaPlugin()
        .addPostfix(
          "idea {",
          "  module {",
          "    settings {",
          "      packagePrefix['src/main/java'] = 'prefix.package.other'",
          "    }",
          "  }",
          "}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.other");
  }


  @Test
  public void testModuleTypesImport() throws Exception {
    importProject(
      createBuildScriptBuilder()
        .withGradleIdeaExtPluginIfCan()
        .withJavaPlugin()
        .addPostfix(
          "import org.jetbrains.gradle.ext.*",
          "idea.module.settings {",
          "    rootModuleType = 'EMPTY_MODULE'",
          "    moduleType[sourceSets.main] = 'WEB_MODULE'",
          "    }"
        ).generate());
    assertThat(getModule("project").getModuleTypeName()).isEqualTo("EMPTY_MODULE");
    assertThat(getModule("project.main").getModuleTypeName()).isEqualTo("WEB_MODULE");
    assertThat(getModule("project.test").getModuleTypeName()).isEqualTo("JAVA_MODULE");
  }
}
