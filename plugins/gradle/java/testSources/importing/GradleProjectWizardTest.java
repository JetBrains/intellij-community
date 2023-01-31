// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.SdkTestFixture;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.UIUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.KotlinDslGradleBuildScriptBuilder;
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory;
import org.jetbrains.plugins.gradle.testFramework.util.GradleFileTestUtil;
import org.jetbrains.plugins.gradle.util.GradleImportingTestUtil;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import static com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.getJavaBuildSystemData;
import static com.intellij.ide.wizard.LanguageNewProjectWizardData.getLanguageData;
import static com.intellij.ide.wizard.NewProjectWizardBaseData.getBaseData;
import static com.intellij.testFramework.utils.module.ModuleAssertionsKt.assertModules;
import static org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.getJavaGradleData;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

public class GradleProjectWizardTest extends NewProjectWizardTestCase {
  private SdkTestFixture gradleJvmFixture = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    gradleJvmFixture = GradleTestFixtureFactory.getFixtureFactory()
      .createGradleJvmTestFixture(GradleVersion.current());
    gradleJvmFixture.setUp();

    VfsRootAccess.allowRootAccess(getTestRootDisposable(), PathManager.getConfigPath());
    var javaHome = ExternalSystemJdkUtil.getJavaHome();
    if (javaHome != null) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), javaHome);
    }
  }

  @Override
  public void tearDown() {
    RunAll.runAll(
      () -> gradleJvmFixture.tearDown(),
      () -> TestDialogManager.setTestDialog(TestDialog.DEFAULT),
      super::tearDown
    );
  }

  public void testGradleNPWPropertiesSuggestion() throws Exception {
    Project project = createProjectFromTemplate(UIBundle.message("label.project.wizard.empty.project.generator.name"), step -> {
      getBaseData(step).setName("project");
    });
    assertModules(project, "project");

    var projectPath = project.getBasePath();
    var externalProjectPath1 = projectPath + "/untitled";
    var externalProjectPath2 = projectPath + "/untitled1";
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        assertNull(getJavaGradleData(step).getParentData());
        assertEquals("untitled", getBaseData(step).getName());
        assertEquals(projectPath, getBaseData(step).getPath());
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        assertNull(getJavaGradleData(step).getParentData());
        assertEquals("untitled1", getBaseData(step).getName());
        assertEquals(projectPath, getBaseData(step).getPath());
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });
    assertModules(
      project, "project",
      "untitled", "untitled.main", "untitled.test",
      "untitled1", "untitled1.main", "untitled1.test"
    );

    DataNode<ProjectData> projectNode1 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, externalProjectPath1);
    DataNode<ProjectData> projectNode2 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, externalProjectPath2);
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        getJavaGradleData(step).setParentData(projectNode1.getData());
        assertEquals("untitled2", getBaseData(step).getName());
        assertEquals(externalProjectPath1, getBaseData(step).getPath());
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        getJavaGradleData(step).setParentData(projectNode2.getData());
        assertEquals("untitled2", getBaseData(step).getName());
        assertEquals(externalProjectPath2, getBaseData(step).getPath());
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });
    assertModules(
      project, "project",
      "untitled", "untitled.main", "untitled.test",
      "untitled1", "untitled1.main", "untitled1.test",
      "untitled.untitled2", "untitled.untitled2.main", "untitled.untitled2.test",
      "untitled1.untitled2", "untitled1.untitled2.main", "untitled1.untitled2.test"
    );
  }

  public void testGradleProject() throws Exception {
    var projectName = "testProject";
    var project = GradleImportingTestUtil.waitForProjectReload(() -> {
      return createProjectFromTemplate(step -> {
        getBaseData(step).setName(projectName);
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });

    assertEquals(projectName, project.getName());
    assertModules(project, projectName, projectName + ".main", projectName + ".test");

    var modules = ModuleManager.getInstance(project).getModules();
    var module = ContainerUtil.find(modules, it -> it.getName().equals(projectName));
    assertTrue(ModuleRootManager.getInstance(module).isSdkInherited());

    var root = ProjectUtil.guessProjectDir(project);
    assertEquals(projectName, root.getName());

    var settingsFile = GradleFileTestUtil.getSettingsFile(root, ".", true);
    assertEquals(
      new GradleSettingScriptBuilderImpl()
        .setProjectName(projectName)
        .generate(true),
      StringUtil.convertLineSeparators(VfsUtilCore.loadText(settingsFile)).trim()
    );

    var buildFile = GradleFileTestUtil.getBuildFile(root, ".", true);
    assertEquals(
      KotlinDslGradleBuildScriptBuilder.create(GradleVersion.current())
        .withJavaPlugin()
        .withJUnit()
        .addGroup("org.example")
        .addVersion("1.0-SNAPSHOT")
        .generate(),
      StringUtil.convertLineSeparators(VfsUtilCore.loadText(buildFile)).trim()
    );

    var settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID);
    assertEquals(1, settings.getLinkedProjectsSettings().size());

    Module childModule = GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        getLanguageData(step).setLanguage("Java");
        getJavaBuildSystemData(step).setBuildSystem("Gradle");
        assertEquals(projectName, getJavaGradleData(step).getParentData().getExternalName());
        getJavaGradleData(step).setArtifactId("childModule");
        getJavaGradleData(step).setAddSampleCode(false);
      });
    });
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

    assertModules(project, projectName, projectName + ".main", projectName + ".test",
                  projectName + ".childModule", projectName + ".childModule.main", projectName + ".childModule.test");

    assertEquals("childModule", childModule.getName());

    assertEquals(
      new GradleSettingScriptBuilderImpl()
        .setProjectName(projectName)
        .include(childModule.getName())
        .generate(true),
      StringUtil.convertLineSeparators(VfsUtilCore.loadText(settingsFile)).trim()
    );
  }

  @Override
  protected Project createProject(Consumer<? super Step> adjuster) throws IOException {
    Project project = super.createProject(adjuster);
    Disposer.register(getTestRootDisposable(), () -> PathKt.delete(ProjectUtil.getExternalConfigurationDir(project)));
    return project;
  }

  @Override
  protected void createWizard(@Nullable Project project) throws IOException {
    if (project != null) {
      LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      localFileSystem.refreshAndFindFileByPath(project.getBasePath());
    }
    File directory = project == null ? createTempDirectoryWithSuffix("New").toFile() : null;
    if (myWizard != null) {
      Disposer.dispose(myWizard.getDisposable());
      myWizard = null;
    }
    myWizard = createWizard(project, directory);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }
}
