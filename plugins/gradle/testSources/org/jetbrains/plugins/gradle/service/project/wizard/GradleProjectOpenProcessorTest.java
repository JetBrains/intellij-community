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
package org.jetbrains.plugins.gradle.service.project.wizard;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeOnEdt;

/**
 * @author Vladislav.Soroka
 * @since 3/20/2017
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleProjectOpenProcessorTest extends GradleImportingTestCase {

  /**
   * Needed only to reuse stuff in GradleImportingTestCase#setUp().
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() throws Throwable {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
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

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
          if (GRADLE_JDK_NAME.equals(sdk.getName())) continue;
          ProjectJdkTable.getInstance().removeJdk(sdk);
        }
      }
    }.execute();

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
      System.out.println();
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