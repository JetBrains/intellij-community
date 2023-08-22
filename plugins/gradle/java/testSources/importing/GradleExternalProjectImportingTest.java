// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BuildViewTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalTask;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vladislav.Soroka
 */
public class GradleExternalProjectImportingTest extends GradleImportingTestCase {

  private boolean failTestOnImportFailure = true;

  @Test
  public void testDummyJarTask() throws Exception {
    importProject(
      "task myJar(type: Jar)"
    );

    assertModules("project");

    ExternalProject externalProject = ExternalProjectDataCache.getInstance(myProject).getRootExternalProject(getProjectPath());
    ExternalTask task = externalProject.getTasks().get("myJar");
    assertEquals(":myJar", task.getQName());

    assertEquals(GradleCommonClassNames.GRADLE_API_TASKS_BUNDLING_JAR, task.getType());
  }

  @Test
  public void testProjectImportUsingNonRootProjectPath() throws Exception {
    // reuse generated wrapper for root project
    FileUtil.copyDir(file("gradle"), file("../gradle"));

    createProjectSubFile("../settings.gradle", "rootProject.name = 'root'\n" +
                                               "include 'project', 'another_project'");
    createProjectSubFile("../build.gradle", "allprojects { apply plugin: 'java' }");
    importProject("");

    assertModules("root", "root.main", "root.test",
                  "root.project", "root.project.main", "root.project.test",
                  "root.another_project", "root.another_project.main", "root.another_project.test");

    ExternalProject externalProject = ExternalProjectDataCache.getInstance(myProject).getRootExternalProject(getProjectPath());
    assertEquals("root", externalProject.getName());
  }

  @TargetVersions("4.1+")
  @Test
  public void testModelBuilderFailure() throws Exception {
    createProjectSubFile("gradle.properties", "systemProp.idea.internal.failEsModelBuilder=true");
    BuildViewTestFixture buildViewTestFixture = new BuildViewTestFixture(myProject);
    try {
      failTestOnImportFailure = false;

      buildViewTestFixture.setUp();
      importProject("");
      buildViewTestFixture.assertSyncViewTreeEquals("""
                                                      -
                                                       -failed
                                                        Boom!""");
      buildViewTestFixture.assertSyncViewSelectedNode("Boom!", true, s -> {
        assertThat(s).startsWith("Boom!\n");
        return null;
      });
    }
    finally {
      failTestOnImportFailure = true;
      buildViewTestFixture.tearDown();
    }
  }

  @Override
  protected void handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    if (failTestOnImportFailure) {
      super.handleImportFailure(errorMessage, errorDetails);
    }
  }
}
