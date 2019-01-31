// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.startup.StartupActivity.POST_STARTUP_ACTIVITY;

/**
 * @author Dmitry Avdeev
 */
public class ProjectOpeningTest extends PlatformTestCase {
  public void testOpenProjectCancelling() throws Exception {
    File foo = createTempDir("foo");
    Project project = null;
    MyStartupActivity activity = new MyStartupActivity();
    PlatformTestUtil.registerExtension(POST_STARTUP_ACTIVITY, activity, getTestRootDisposable());

    try {
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, foo.getPath());
      assertFalse(manager.openProject(project));
      assertFalse(project.isOpen());
      assertTrue(activity.passed);
    }
    finally {
      closeProject(project);
    }
  }

  public void testCancelOnLoadingModules() throws Exception {
    File foo = createTempDir("foo");
    Project project = null;
    try {
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, foo.getPath());
      project.save();
      closeProject(project);

      ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
        @Override
        public void projectComponentsInitialized(@NotNull Project project) {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          assertNotNull(indicator);
          indicator.cancel();
          indicator.checkCanceled();
        }
      });

      project = manager.loadAndOpenProject(foo.getPath());
      assertFalse(project.isOpen());
      assertTrue(project.isDisposed());
    }
    finally {
      closeProject(project);
    }
  }

  public void testIsSameProjectForDirectoryBasedProject() throws IOException {
    File projectDir = createTempDir("project");
    Project dirBasedProject = ProjectManager.getInstance().createProject("project", projectDir.getAbsolutePath());
    disposeOnTearDown(dirBasedProject);

    assertTrue(ProjectUtil.isSameProject(projectDir.getAbsolutePath(), dirBasedProject));
    assertFalse(ProjectUtil.isSameProject(createTempDir("project2").getAbsolutePath(), dirBasedProject));
    File iprFilePath = new File(projectDir, "project.ipr");
    assertTrue(ProjectUtil.isSameProject(iprFilePath.getAbsolutePath(), dirBasedProject));
    File miscXmlFilePath = new File(projectDir, ".idea/misc.xml");
    assertTrue(ProjectUtil.isSameProject(miscXmlFilePath.getAbsolutePath(), dirBasedProject));
    File someOtherFilePath = new File(projectDir, "misc.xml");
    assertFalse(ProjectUtil.isSameProject(someOtherFilePath.getAbsolutePath(), dirBasedProject));
  }

  public void testIsSameProjectForFileBasedProject() throws IOException {
    File projectDir = createTempDir("project");
    File iprFilePath = new File(projectDir, "project.ipr");
    Project fileBasedProject = ProjectManager.getInstance().createProject(iprFilePath.getName(), iprFilePath.getAbsolutePath());
    disposeOnTearDown(fileBasedProject);

    assertTrue(ProjectUtil.isSameProject(projectDir.getAbsolutePath(), fileBasedProject));
    assertFalse(ProjectUtil.isSameProject(createTempDir("project2").getAbsolutePath(), fileBasedProject));
    File iprFilePath2 = new File(projectDir, "project2.ipr");
    assertFalse(ProjectUtil.isSameProject(iprFilePath2.getAbsolutePath(), fileBasedProject));
  }

  static void closeProject(@Nullable Project project) {
    if (project != null && !project.isDisposed()) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
    }
  }

  private static class MyStartupActivity implements StartupActivity, DumbAware {
    private boolean passed;
    @Override
    public void runActivity(@NotNull Project project) {
      passed = true;
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      assertNotNull(indicator);
      indicator.cancel();
    }
  }
}
