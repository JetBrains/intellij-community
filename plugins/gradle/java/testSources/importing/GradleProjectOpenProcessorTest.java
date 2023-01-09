// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.ExternalStorageConfigurationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.EdtTestUtilKt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleImportingTestUtil;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeOnEdt;

/**
 * @author Vladislav.Soroka
 */
public class GradleProjectOpenProcessorTest extends GradleImportingTestCase {
  /**
   * Needed only to reuse stuff in GradleImportingTestCase#setUp().
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Override
  protected void collectAllowedRoots(List<String> roots) {
    super.collectAllowedRoots(roots);
    for (String javaHome : JavaSdk.getInstance().suggestHomePaths()) {
      roots.add(javaHome);
      roots.addAll(collectRootsInside(javaHome));
    }
  }

  @Test
  public void testGradleSettingsFileModification() throws Exception {
    VirtualFile foo = createProjectSubDir("foo");
    createProjectSubFile("foo/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("foo/.idea/modules.xml",
                         """
                           <?xml version="1.0" encoding="UTF-8"?>
                           <project version="4">
                             <component name="ProjectModuleManager">
                               <modules>
                                 <module fileurl="file://$PROJECT_DIR$/foo.iml" filepath="$PROJECT_DIR$/foo.iml" />
                                 <module fileurl="file://$PROJECT_DIR$/bar.iml" filepath="$PROJECT_DIR$/bar.iml" />
                               </modules>
                             </component>
                           </project>""");
    createProjectSubFile("foo/foo.iml",
                         """
                           <?xml version="1.0" encoding="UTF-8"?>
                           <module type="JAVA_MODULE" version="4">
                             <component name="NewModuleRootManager" inherit-compiler-output="true">
                               <content url="file://$MODULE_DIR$">
                               </content>
                             </component>
                           </module>""");
    createProjectSubFile("foo/bar.iml",
                         """
                           <?xml version="1.0" encoding="UTF-8"?>
                           <module type="JAVA_MODULE" version="4">
                             <component name="NewModuleRootManager" inherit-compiler-output="true">
                             </component>
                           </module>""");

    Project fooProject = PlatformTestUtil.loadAndOpenProject(foo.toNioPath(), getTestRootDisposable());
    AutoImportProjectTracker.enableAutoReloadInTests(getTestRootDisposable());

    try {
      edt(() -> UIUtil.dispatchAllInvocationEvents());
      assertModules(fooProject, "foo", "bar");

      GradleImportingTestUtil.waitForProjectReload(() -> {
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
        return null;
      });
      assertTrue("The module has not been linked",
                 ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, getModule(fooProject, "foo")));
    }
    finally {
      edt(() -> closeProject(fooProject));
    }
    assertFalse(fooProject.isOpen());
    assertTrue(fooProject.isDisposed());
  }

  @Test
  public void testDefaultGradleSettings() throws IOException {
    VirtualFile foo = createProjectSubDir("foo");
    createProjectSubFile("foo/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("foo/settings.gradle", "");
    Project fooProject = executeOnEdt(() -> ProjectUtil.openOrImport(foo.toNioPath()));

    try {
      assertTrue(fooProject.isOpen());
      edt(() -> UIUtil.dispatchAllInvocationEvents());
      assertTrue("The module has not been linked",
                 ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, getModule(fooProject, "foo")));
      assertTrue(ExternalStorageConfigurationManager.getInstance(fooProject).isEnabled());
      assertTrue(GradleSettings.getInstance(fooProject).getStoreProjectFilesExternally());
      GradleProjectSettings fooSettings = GradleSettings.getInstance(fooProject).getLinkedProjectSettings(foo.getPath());
      assertTrue(fooSettings.isResolveModulePerSourceSet());
      assertTrue(fooSettings.isResolveExternalAnnotations());
      assertTrue(fooSettings.getDelegatedBuild());
      assertEquals(TestRunner.GRADLE, fooSettings.getTestRunner());
      assertTrue(fooSettings.isUseQualifiedModuleNames());
    }
    finally {
      edt(() -> closeProject(fooProject));
    }
    assertFalse(fooProject.isOpen());
    assertTrue(fooProject.isDisposed());
  }

  @Test
  public void testOpenAndImportProjectInHeadlessMode() throws Exception {
    VirtualFile foo = createProjectSubDir("foo");
    createProjectSubFile("foo/build.gradle", "apply plugin: 'java'");
    createProjectSubFile("foo/.idea/inspectionProfiles/myInspections.xml",
                         """
                           <component name="InspectionProjectProfileManager">
                             <profile version="1.0">
                               <option name="myName" value="myInspections" />
                               <option name="myLocal" value="true" />
                               <inspection_tool class="MultipleRepositoryUrls" enabled="true" level="ERROR" enabled_by_default="true" />
                             </profile>
                           </component>""");
    createProjectSubFile("foo/.idea/inspectionProfiles/profiles_settings.xml",
                         """
                           <component name="InspectionProjectProfileManager">
                             <settings>
                               <option name="PROJECT_PROFILE" value="myInspections" />
                               <version value="1.0" />
                             </settings>
                           </component>""");
    FileUtil.copyDir(new File(getProjectPath(), "gradle"), new File(getProjectPath(), "foo/gradle"));

    Project fooProject = null;
    try {
      fooProject = EdtTestUtilKt.runInEdtAndGet(() -> {
        final Project project = ProjectUtil.openOrImport(foo.toNioPath());
        ProjectInspectionProfileManager.getInstance(project).forceLoadSchemes();
        UIUtil.dispatchAllInvocationEvents();
        return project;
      });
      assertTrue(fooProject.isOpen());
      InspectionProfileImpl currentProfile = getCurrentProfile(fooProject);
      assertEquals("myInspections", currentProfile.getName());
      ScopeToolState toolState = currentProfile.getToolDefaultState("MultipleRepositoryUrls", fooProject);
      assertEquals(HighlightDisplayLevel.ERROR, toolState.getLevel());

      // Gradle import will fail because of classloading limitation of the test mode since wrong guava pollute the classpath
      // assertModules(fooProject, "foo", "foo_main", "foo_test");
    }
    finally {
      if (fooProject != null) {
        Project finalFooProject = fooProject;
        edt(() -> closeProject(finalFooProject));
      }
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
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
    }
  }
}