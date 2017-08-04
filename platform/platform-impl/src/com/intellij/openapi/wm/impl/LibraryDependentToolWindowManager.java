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
package com.intellij.openapi.wm.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class LibraryDependentToolWindowManager implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() ||
        application.isHeadlessEnvironment()) {
      return;
    }

    final ModuleRootListener rootListener = new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        checkToolWindowStatuses(project);
      }
    };

    checkToolWindowStatuses(project);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener);
  }

  private static void checkToolWindowStatuses(@NotNull final Project project) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (project.isDisposed()) return;

      doCheckToolWindowStatuses(project);
    });
  }

  private static void doCheckToolWindowStatuses(@NotNull final Project project) {
    final ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(project);

    for (LibraryDependentToolWindow libraryToolWindow : Extensions.getExtensions(LibraryDependentToolWindow.EXTENSION_POINT_NAME)) {
      boolean exists = DumbService.getInstance(project)
        .runReadActionInSmartMode(() -> libraryToolWindow.getLibrarySearchHelper().isLibraryExists(project));

      ApplicationManager.getApplication().invokeLater(() -> {
        ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(libraryToolWindow.id);

        if (exists) {
          if (toolWindow == null) {
            toolWindowManagerEx.initToolWindow(libraryToolWindow);
          }
        }
        else {
          if (toolWindow != null) {
            toolWindowManagerEx.unregisterToolWindow(libraryToolWindow.id);
          }
        }
      });
    }
  }
}
