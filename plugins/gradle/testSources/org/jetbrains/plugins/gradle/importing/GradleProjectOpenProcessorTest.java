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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeOnEdt;

/**
 * @author Vladislav.Soroka
 * @since 3/20/2017
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleProjectOpenProcessorTest extends GradleImportingTestCase {

  private List<Sdk> removedSdks = new SmartList<>();

  /**
   * Needed only to reuse stuff in GradleImportingTestCase#setUp().
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) {
        for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
          if (GRADLE_JDK_NAME.equals(sdk.getName())) continue;
          ProjectJdkTable.getInstance().removeJdk(sdk);
          removedSdks.add(sdk);
        }
      }
    }.execute();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          for (Sdk sdk : removedSdks) {
            SdkConfigurationUtil.addSdk(sdk);
          }
          removedSdks.clear();
        }
      }.execute();
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testGradleSettingsFileModification() throws IOException {
    VirtualFile foo = createProjectSubDir("foo");
    createProjectSubFile("foo/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("foo/.idea/modules.xml",
                         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<project version=\"4\">\n" +
                         "  <component name=\"ProjectModuleManager\">\n" +
                         "    <modules>\n" +
                         "      <module fileurl=\"file://$PROJECT_DIR$/foo.iml\" filepath=\"$PROJECT_DIR$/foo.iml\" />\n" +
                         "      <module fileurl=\"file://$PROJECT_DIR$/bar.iml\" filepath=\"$PROJECT_DIR$/bar.iml\" />\n" +
                         "    </modules>\n" +
                         "  </component>\n" +
                         "</project>");
    createProjectSubFile("foo/foo.iml",
                         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
                         "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
                         "    <content url=\"file://$MODULE_DIR$\">\n" +
                         "    </content>\n" +
                         "  </component>\n" +
                         "</module>");
    createProjectSubFile("foo/bar.iml",
                         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
                         "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
                         "  </component>\n" +
                         "</module>");

    Project fooProject = executeOnEdt(() -> ProjectUtil.openProject(foo.getPath(), null, true));

    try {
      assertTrue(fooProject.isOpen());
      edt(() -> UIUtil.dispatchAllInvocationEvents());
      assertModules(fooProject, "foo", "bar");

      Semaphore semaphore = new Semaphore(1);
      final MessageBusConnection myBusConnection = fooProject.getMessageBus().connect();
      myBusConnection.subscribe(ProjectDataImportListener.TOPIC, path -> semaphore.up());
      createProjectSubFile("foo/.idea/gradle.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                           "<project version=\"4\">\n" +
                           "  <component name=\"GradleSettings\">\n" +
                           "    <option name=\"linkedExternalProjectsSettings\">\n" +
                           "      <GradleProjectSettings>\n" +
                           "        <option name=\"distributionType\" value=\"DEFAULT_WRAPPED\" />\n" +
                           "        <option name=\"externalProjectPath\" value=\"$PROJECT_DIR$\" />\n" +
                           "        <option name=\"gradleJvm\" value=\"" + GRADLE_JDK_NAME + "\" />\n" +
                           "        <option name=\"modules\">\n" +
                           "          <set>\n" +
                           "            <option value=\"$PROJECT_DIR$\" />\n" +
                           "          </set>\n" +
                           "        </option>\n" +
                           "        <option name=\"resolveModulePerSourceSet\" value=\"false\" />\n" +
                           "      </GradleProjectSettings>\n" +
                           "    </option>\n" +
                           "  </component>\n" +
                           "</project>");
      edt(() -> UIUtil.dispatchAllInvocationEvents());
      edt(() -> PlatformTestUtil.saveProject(fooProject));
      assert semaphore.waitFor(100000);
      assertTrue("The module has not been linked",
                 ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, getModule(fooProject, "foo")));
    }
    finally {
      edt(() -> closeProject(fooProject));
    }
    assertFalse(fooProject.isOpen());
    assertTrue(fooProject.isDisposed());

    //edt(() -> PlatformTestUtil.saveProject(myProject));
    //importProject("apply plugin: 'java'");
    //assertModules("project", "project_main", "project_test");
  }

  @Test
  public void testOpenAndImportProjectInHeadlessMode() throws Exception {
    VirtualFile foo = createProjectSubDir("foo");
    createProjectSubFile("foo/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("foo/.idea/inspectionProfiles/myInspections.xml",
                         "<component name=\"InspectionProjectProfileManager\">\n" +
                         "  <profile version=\"1.0\">\n" +
                         "    <option name=\"myName\" value=\"myInspections\" />\n" +
                         "    <option name=\"myLocal\" value=\"true\" />\n" +
                         "    <inspection_tool class=\"MultipleRepositoryUrls\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                         "  </profile>\n" +
                         "</component>");
    createProjectSubFile("foo/.idea/inspectionProfiles/profiles_settings.xml",
                         "<component name=\"InspectionProjectProfileManager\">\n" +
                         "  <settings>\n" +
                         "    <option name=\"PROJECT_PROFILE\" value=\"myInspections\" />\n" +
                         "    <version value=\"1.0\" />\n" +
                         "  </settings>\n" +
                         "</component>");

    Project fooProject = executeOnEdt(() -> {
      Project project = ProjectUtil.openOrImport(foo.getPath(), null, true);
      ProjectInspectionProfileManager projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project);
      projectInspectionProfileManager.forceLoadSchemes();
      return project;
    });
    try {
      assertTrue(fooProject.isOpen());
      edt(() -> UIUtil.dispatchAllInvocationEvents());
      InspectionProfileImpl currentProfile = getCurrentProfile(fooProject);
      assertEquals("myInspections", currentProfile.getName());
      ScopeToolState toolState = currentProfile.getToolDefaultState("MultipleRepositoryUrls", fooProject);
      assertEquals(HighlightDisplayLevel.ERROR, toolState.getLevel());
      assertModules(fooProject, "foo", "foo_main", "foo_test");
    }
    finally {
      edt(() -> closeProject(fooProject));
    }
    assertFalse(fooProject.isOpen());
    assertTrue(fooProject.isDisposed());
  }

  @NotNull
  private static InspectionProfileImpl getCurrentProfile(Project fooProject) {
    InspectionProfileImpl currentProfile = InspectionProfileManager.getInstance(fooProject).getCurrentProfile();
    if (!currentProfile.wasInitialized()) {
      boolean oldValue = InspectionProfileImpl.INIT_INSPECTIONS;
      try {
        InspectionProfileImpl.INIT_INSPECTIONS = true;
        currentProfile.initInspectionTools(fooProject);
      }
      finally {
        InspectionProfileImpl.INIT_INSPECTIONS = oldValue;
      }
    }
    return currentProfile;
  }

  private static void closeProject(final Project project) {
    if (project != null && !project.isDisposed()) {
      ProjectManager.getInstance().closeProject(project);
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
    }
  }
}