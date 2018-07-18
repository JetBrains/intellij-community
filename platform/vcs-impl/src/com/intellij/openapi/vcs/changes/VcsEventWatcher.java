// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class VcsEventWatcher implements ProjectComponent {
  private final Project myProject;

  public VcsEventWatcher(Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() ->
            VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty(), ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });

    connection.subscribe(ProblemListener.TOPIC, new ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull final VirtualFile file) {
        ChangesViewManager.getInstance(myProject).refreshChangesViewNodeAsync(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        ChangesViewManager.getInstance(myProject).refreshChangesViewNodeAsync(file);
      }
    });
  }
}