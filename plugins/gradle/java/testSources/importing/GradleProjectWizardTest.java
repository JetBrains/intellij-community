// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData;
import com.intellij.ide.wizard.LanguageNewProjectWizardData;
import com.intellij.ide.wizard.NewProjectWizardBaseData;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.environment.Environment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData;
import org.jetbrains.plugins.gradle.util.GradleImportingTestUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase.collectRootsInside;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

public class GradleProjectWizardTest extends NewProjectWizardTestCase {
  private static final String GRADLE_JDK_NAME = "Gradle JDK";
  private final List<Sdk> removedSdks = new SmartList<>();
  private String myJdkHome;

  public void testGradleNPWPropertiesSuggestion() throws Exception {
    Project project = createProjectFromTemplate(IdeBundle.message("empty.project.generator.name"), null, null);
    assertModules(project);

    var projectPath = project.getBasePath();
    var externalProjectPath1 = projectPath + "/untitled";
    var externalProjectPath2 = projectPath + "/untitled1";
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        assertNull(GradleJavaNewProjectWizardData.getParentData(step));
        assertEquals("untitled", NewProjectWizardBaseData.getName(step));
        assertEquals(projectPath, NewProjectWizardBaseData.getPath(step));
      });
    });
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        assertNull(GradleJavaNewProjectWizardData.getParentData(step));
        assertEquals("untitled1", NewProjectWizardBaseData.getName(step));
        assertEquals(projectPath, NewProjectWizardBaseData.getPath(step));
      });
    });
    assertModules(
      project,
      "untitled", "untitled.main", "untitled.test",
      "untitled1", "untitled1.main", "untitled1.test"
    );

    DataNode<ProjectData> projectNode1 = ExternalSystemApiUtil.findProjectData(project, SYSTEM_ID, externalProjectPath1);
    DataNode<ProjectData> projectNode2 = ExternalSystemApiUtil.findProjectData(project, SYSTEM_ID, externalProjectPath2);
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        GradleJavaNewProjectWizardData.setParentData(step, projectNode1.getData());
        assertEquals("untitled2", NewProjectWizardBaseData.getName(step));
        assertEquals(externalProjectPath1, NewProjectWizardBaseData.getPath(step));
      });
    });
    GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        GradleJavaNewProjectWizardData.setParentData(step, projectNode2.getData());
        assertEquals("untitled2", NewProjectWizardBaseData.getName(step));
        assertEquals(externalProjectPath2, NewProjectWizardBaseData.getPath(step));
      });
    });
    assertModules(
      project,
      "untitled", "untitled.main", "untitled.test",
      "untitled1", "untitled1.main", "untitled1.test",
      "untitled.untitled2", "untitled.untitled2.main", "untitled.untitled2.test",
      "untitled1.untitled2", "untitled1.untitled2.main", "untitled1.untitled2.test"
    );
  }

  public void testGradleProject() throws Exception {
    final String projectName = "testProject";
    Project project = GradleImportingTestUtil.waitForProjectReload(() -> {
      return createProjectFromTemplate(step -> {
        NewProjectWizardBaseData.setName(step, projectName);
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        GradleJavaNewProjectWizardData.setGroupId(step, "");
      });
    });

    assertEquals(projectName, project.getName());
    assertModules(project, projectName, projectName + ".main", projectName + ".test");
    Module[] modules = ModuleManager.getInstance(project).getModules();
    final Module module = ContainerUtil.find(modules, it -> it.getName().equals(projectName));
    assertTrue(ModuleRootManager.getInstance(module).isSdkInherited());

    VirtualFile root = ProjectRootManager.getInstance(project).getContentRoots()[0];
    VirtualFile settingsScript = VfsUtilCore.findRelativeFile("settings.gradle", root);
    assertNotNull(settingsScript);
    assertEquals(String.format("rootProject.name = '%s'\n\n", projectName),
                 StringUtil.convertLineSeparators(VfsUtilCore.loadText(settingsScript)));

    VirtualFile buildScript = VfsUtilCore.findRelativeFile("build.gradle", root);
    assertNotNull(buildScript);
    assertEquals("plugins {\n" +
                 "    id 'java'\n" +
                 "}\n" +
                 "\n" +
                 "version '1.0-SNAPSHOT'\n" +
                 "\n" +
                 "repositories {\n" +
                 "    mavenCentral()\n" +
                 "}\n" +
                 "\n" +
                 "dependencies {\n" +
                 "    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'\n" +
                 "    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.1'\n" +
                 "}\n" +
                 "\n" +
                 "test {\n" +
                 "    useJUnitPlatform()\n" +
                 "}",
                 StringUtil.convertLineSeparators(VfsUtilCore.loadText(buildScript)));


    AbstractExternalSystemSettings<?, ?, ?> settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID);
    assertEquals(1, settings.getLinkedProjectsSettings().size());

    Module childModule = GradleImportingTestUtil.waitForProjectReload(() -> {
      return createModuleFromTemplate(project, step -> {
        LanguageNewProjectWizardData.setLanguage(step, "Java");
        BuildSystemJavaNewProjectWizardData.setBuildSystem(step, "Gradle");
        assertEquals(projectName, GradleJavaNewProjectWizardData.getParentData(step).getExternalName());
        GradleJavaNewProjectWizardData.setArtifactId(step, "childModule");
        GradleJavaNewProjectWizardData.setGroupId(step, "");
      });
    });
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

    assertModules(project, projectName, projectName + ".main", projectName + ".test",
                  projectName + ".childModule", projectName + ".childModule.main", projectName + ".childModule.test");

    assertEquals("childModule", childModule.getName());
    assertEquals(String.format("rootProject.name = '%s'\n" +
                               "include '%s'\n\n", projectName, childModule.getName()),
                 StringUtil.convertLineSeparators(VfsUtilCore.loadText(settingsScript)));
  }

  private static void assertModules(@NotNull Project project, String @NotNull ... expectedNames) {
    Module[] actual = ModuleManager.getInstance(project).getModules();
    Collection<String> actualNames = ContainerUtil.map(actual, it -> it.getName());
    assertEquals(ContainerUtil.newHashSet(expectedNames), new HashSet<>(actualNames));
  }

  @Override
  protected Project createProject(Consumer adjuster) throws IOException {
    @SuppressWarnings("unchecked")
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

  private void collectAllowedRoots(final List<String> roots) {
    roots.add(myJdkHome);
    roots.addAll(collectRootsInside(myJdkHome));
    roots.add(PathManager.getConfigPath());
    String javaHome = Environment.getVariable("JAVA_HOME");
    if (javaHome != null) roots.add(javaHome);
  }

  @Override
  protected void setUp() throws Exception {
    myJdkHome = IdeaTestUtil.requireRealJdkHome();
    super.setUp();
    removedSdks.clear();
    WriteAction.runAndWait(() -> {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
        if (GRADLE_JDK_NAME.equals(sdk.getName())) continue;
        removedSdks.add(sdk);
      }
      VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
      JavaSdk javaSdk = JavaSdk.getInstance();
      SdkType javaSdkType = javaSdk == null ? SimpleJavaSdkType.getInstance() : javaSdk;
      Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, javaSdkType, true, null, GRADLE_JDK_NAME);
      assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
      ProjectJdkTable.getInstance().addJdk(jdk);
    });
    List<String> allowedRoots = new ArrayList<>();
    collectAllowedRoots(allowedRoots);
    if (!allowedRoots.isEmpty()) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), ArrayUtilRt.toStringArray(allowedRoots));
    }
  }

  @Override
  public void tearDown() {
    if (myJdkHome == null) {
      //super.setUp() wasn't called
      return;
    }
    new RunAll(
      () -> {
        WriteAction.runAndWait(() -> {
          Arrays.stream(ProjectJdkTable.getInstance().getAllJdks()).forEach(ProjectJdkTable.getInstance()::removeJdk);
          for (Sdk sdk : removedSdks) {
            SdkConfigurationUtil.addSdk(sdk);
          }
          removedSdks.clear();
        });
      },
      () -> {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT);
      },
      super::tearDown
    ).run();
  }
}
