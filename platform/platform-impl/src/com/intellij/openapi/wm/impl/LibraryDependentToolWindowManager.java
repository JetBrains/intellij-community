package com.intellij.openapi.wm.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class LibraryDependentToolWindowManager extends AbstractProjectComponent {
  private final ToolWindowManagerEx myToolWindowManager;

  protected LibraryDependentToolWindowManager(Project project,
                                              ToolWindowManagerEx toolWindowManager) {
    super(project);
    myToolWindowManager = toolWindowManager;
  }

  @Override
  public void projectOpened() {
    final ModuleRootListener rootListener = new ModuleRootAdapter() {
      public void rootsChanged(ModuleRootEvent event) {
        if (!myProject.isDisposed()) {
          checkToolWindowStatuses(myProject);
        }
      }
    };

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        if (!myProject.isDisposed()) {
          checkToolWindowStatuses(myProject);
          final MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
          connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener);
        }
      }
    });
  }

  private void checkToolWindowStatuses(@NotNull final Project project) {
    assert !project.isDisposed();

    DumbService.getInstance(project).smartInvokeLater(new Runnable() {
      public void run() {
        for (LibraryDependentToolWindow libraryToolWindow : Extensions.getExtensions(LibraryDependentToolWindow.EXTENSION_POINT_NAME)) {
          boolean exists;
          try {
            exists = libraryToolWindow.getLibrarySearchHelper().isLibraryExists(project);
          }
          catch (ProcessCanceledException e) {
            exists = false;
            DumbService.getInstance(project).smartInvokeLater(this);
          }
          if (exists) {
            ensureToolWindowExists(libraryToolWindow);
          }
          else {
            ToolWindow toolWindow = myToolWindowManager.getToolWindow(libraryToolWindow.id);
            if (toolWindow != null) {
              myToolWindowManager.unregisterToolWindow(libraryToolWindow.id);
            }
          }
        }
      }
    });
  }

  private void ensureToolWindowExists(LibraryDependentToolWindow extension) {
    ToolWindow toolWindow = myToolWindowManager.getToolWindow(extension.id);
    if (toolWindow == null) {
      myToolWindowManager.initToolWindow(extension);
    }
  }
}
