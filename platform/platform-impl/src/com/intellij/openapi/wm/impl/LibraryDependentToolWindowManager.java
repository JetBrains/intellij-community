// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.openapi.wm.ext.LibrarySearchHelper;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;

public class LibraryDependentToolWindowManager implements StartupActivity {

  private static final Executor ourExecutor =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LibraryDependentToolWindowManager");

  @Override
  public void runActivity(@NotNull Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() ||
        application.isHeadlessEnvironment()) {
      return;
    }

    final ModuleRootListener rootListener = new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        checkToolWindowStatuses(project);
      }
    };

    checkToolWindowStatuses(project);

    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener);
  }

  private void checkToolWindowStatuses(@NotNull final Project project) {
    final ModalityState currentModalityState = ModalityState.current();

    List<LibraryDependentToolWindow> extensions = LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList();

    ReadAction
      .nonBlocking(() -> new HashSet<>(ContainerUtil.findAll(extensions, ltw -> {
        LibrarySearchHelper helper = ltw.getLibrarySearchHelper();
        return helper != null && helper.isLibraryExists(project);
      })))
      .inSmartMode(project)
      .coalesceBy(this)
      .finishOnUiThread(currentModalityState, existing -> {
        ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(project);
        for (LibraryDependentToolWindow libraryToolWindow : extensions) {
          ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(libraryToolWindow.id);
          if (existing.contains(libraryToolWindow)) {
            if (toolWindow == null) {
              toolWindowManagerEx.initToolWindow(libraryToolWindow);
            }
          }
          else {
            if (toolWindow != null) {
              toolWindow.remove();
            }
          }
        }
      })
      .submit(ourExecutor);
  }
}
