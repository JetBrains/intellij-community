// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
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
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

final class LibraryDependentToolWindowManager implements StartupActivity {
  private static final Executor ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LibraryDependentToolWindowManager");

  LibraryDependentToolWindowManager() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
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
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, ((presentableLibraryName, oldRoots, newRoots, libraryNameForDebug) -> checkToolWindowStatuses(project)));
    LibraryDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull LibraryDependentToolWindow extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkToolWindowStatuses(project, extension.id);
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

  private void checkToolWindowStatuses(@NotNull Project project) {
    checkToolWindowStatuses(project, null);
  }

  private void checkToolWindowStatuses(@NotNull Project project, @Nullable String extensionId) {
    ModalityState currentModalityState = ModalityState.current();
    ReadAction
      .nonBlocking(() -> {
        List<LibraryDependentToolWindow> extensions = LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList();
        if (extensionId != null) {
          extensions = ContainerUtil.filter(extensions, ltw -> Objects.equals(ltw.id, extensionId));
        }

        Set<LibraryDependentToolWindow> existing = new HashSet<>(ContainerUtil.findAll(extensions, ltw -> {
          LibrarySearchHelper helper = ltw.getLibrarySearchHelper();
          return helper != null && helper.isLibraryExists(project);
        }));

        return new LibraryWindowsState(project, extensions, existing);
      })
      .inSmartMode(project)
      .coalesceBy(this, project)
      .finishOnUiThread(currentModalityState, LibraryDependentToolWindowManager::applyWindowsState)
      .submit(ourExecutor);
  }

  private static void applyWindowsState(LibraryWindowsState state) {
    ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(state.project);
    for (LibraryDependentToolWindow libraryToolWindow : state.extensions) {
      ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(libraryToolWindow.id);
      if (state.existing.contains(libraryToolWindow)) {
        if (toolWindow == null) {
          toolWindowManagerEx.initToolWindow(libraryToolWindow);

          if (!libraryToolWindow.showOnStripeByDefault) {
            toolWindow = toolWindowManagerEx.getToolWindow(libraryToolWindow.id);
            if (toolWindow != null) {
              WindowInfoImpl windowInfo = toolWindowManagerEx.getLayout().getInfo(libraryToolWindow.id);
              if (windowInfo != null && !windowInfo.isFromPersistentSettings()) {
                toolWindow.setShowStripeButton(false);
              }
            }
          }
        }
      }
      else {
        if (toolWindow != null) {
          toolWindow.remove();
        }
      }
    }
  }

  private static class LibraryWindowsState {
    final @NotNull Project project;
    final List<LibraryDependentToolWindow> extensions;
    final Set<LibraryDependentToolWindow> existing;

    private LibraryWindowsState(@NotNull Project project,
                                List<LibraryDependentToolWindow> extensions,
                                Set<LibraryDependentToolWindow> existing) {
      this.project = project;
      this.extensions = extensions;
      this.existing = existing;
    }
  }
}
