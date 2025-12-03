// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface UndoForeignCommandService {

  void beforeStartForeignCommand(@Nullable FileEditor fileEditor, @Nullable DocumentReference originator);

  void startForeignCommand(@NotNull CommandId commandId);

  void finishForeignCommand();

  boolean isForeignIsProgress();

  @Nullable FileEditor foreignFileEditor();

  @Nullable DocumentReference foreignOriginator();

  static @Nullable UndoForeignCommandService getInstance(@Nullable Project project) {
    if (project == null || project.isDefault()) {
      return getGlobalService();
    } else {
      return getServicePerProject(project);
    }
  }

  private static @Nullable UndoForeignCommandService getGlobalService() {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      return ProgressManager.getInstance().computeInNonCancelableSection(
        () -> application.getService(UndoForeignCommandService.class)
      );
    }
    return null;
  }

  private static @Nullable UndoForeignCommandService getServicePerProject(@NotNull Project project) {
    return ProgressManager.getInstance().computeInNonCancelableSection(
      () -> project.getService(UndoForeignCommandService.class)
    );
  }
}
