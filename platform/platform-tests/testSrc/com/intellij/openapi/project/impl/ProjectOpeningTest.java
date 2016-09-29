/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.startup.StartupActivity.POST_STARTUP_ACTIVITY;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectOpeningTest extends LightPlatformTestCase {

  public void _testOpenProjectCancelling() throws Exception {
    File foo = PlatformTestCase.createTempDir("foo");
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
  /*

  public void testCancelOnLoadingModules() throws Exception {
    File foo = PlatformTestCase.createTempDir("foo");
    Project project = null;
    try {
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, foo.getPath());
      project.save();
      closeProject(project);

      ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
        @Override
        public void projectComponentsInitialized(@NotNull Project project) {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          assertNotNull(indicator);
          indicator.cancel();
        }
      });

      manager.loadAndOpenProject(foo.getPath());
      assertFalse(manager.openProject(project));
      assertFalse(project.isOpen());
    }
    finally {
      closeProject(project);
    }
  }

  */

  private static void closeProject(final Project project) {
    if (project != null && !project.isDisposed()) {
      ProjectManager.getInstance().closeProject(project);
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
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
