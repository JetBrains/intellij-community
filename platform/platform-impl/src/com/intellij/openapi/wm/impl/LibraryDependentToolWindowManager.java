// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.openapi.wm.ext.LibrarySearchHelper;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;

final class LibraryDependentToolWindowManager implements StartupActivity {
  private static final Executor ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LibraryDependentToolWindowManager");

  LibraryDependentToolWindowManager() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    ModuleRootListener rootListener = new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        checkToolWindowStatuses(project);
      }
    };

    checkToolWindowStatuses(project);

    SimpleMessageBusConnection connection = project.getMessageBus().simpleConnect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener);
    LibraryDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<LibraryDependentToolWindow>() {
      @Override
      public void extensionAdded(@NotNull LibraryDependentToolWindow extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkToolWindowStatuses(project, Collections.singletonList(extension));
      }

      @Override
      public void extensionRemoved(@NotNull LibraryDependentToolWindow extension, @NotNull PluginDescriptor pluginDescriptor) {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(extension.id);
        if (window != null) {
          window.remove();
        }
      }
    }, project);
  }

  private void checkToolWindowStatuses(@NotNull final Project project) {
    checkToolWindowStatuses(project, LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList());
  }

  private void checkToolWindowStatuses(@NotNull Project project, @NotNull List<LibraryDependentToolWindow> extensions) {
    ModalityState currentModalityState = ModalityState.current();
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
