// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

public class LibraryDependentToolWindowManager implements StartupActivity {

  private static final ExecutorService ourExecutor =
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
      public void rootsChanged(ModuleRootEvent event) {
        checkToolWindowStatuses(project);
      }
    };

    checkToolWindowStatuses(project);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener);
  }

  private static void checkToolWindowStatuses(@NotNull final Project project) {
    final ModalityState currentModalityState = ModalityState.current();

    ourExecutor.submit(() -> {
      doCheckToolWindowStatuses(project, currentModalityState);
    });
  }

  private static void doCheckToolWindowStatuses(@NotNull final Project project,
                                                ModalityState currentModalityState) {
    final DumbService dumbService = ReadAction.compute(() -> project.isDisposed() ? null : DumbService.getInstance(project));
    if (dumbService == null) return;

    for (LibraryDependentToolWindow libraryToolWindow : LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList()) {
      Ref<Boolean> libraryExists = Ref.create(false);
      Runnable runnable = () -> {
        final Boolean exists = dumbService.runReadActionInSmartMode(() ->
                                                                      !project.isDisposed() &&
                                                                      libraryToolWindow.getLibrarySearchHelper().isLibraryExists(project));
        libraryExists.set(exists);
      };
      while (!project.isDisposed()) {
        boolean finished = ProgressIndicatorUtils.runWithWriteActionPriority(runnable, new ProgressIndicatorBase());
        if (finished) {
          break;
        }
        ProgressIndicatorUtils.yieldToPendingWriteActions();
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        final ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(project);
        ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(libraryToolWindow.id);

        if (libraryExists.get()) {
          if (toolWindow == null) {
            toolWindowManagerEx.initToolWindow(libraryToolWindow);
          }
        }
        else {
          if (toolWindow != null) {
            toolWindowManagerEx.unregisterToolWindow(libraryToolWindow.id);
          }
        }
      }, currentModalityState, project.getDisposed());
    }
  }
}
