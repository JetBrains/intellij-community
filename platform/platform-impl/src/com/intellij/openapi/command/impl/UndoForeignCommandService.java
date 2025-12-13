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

  void beforeStartForeignCommand(@Nullable Project project, @Nullable FileEditor fileEditor, @Nullable DocumentReference originator);

  void startForeignCommand(@NotNull CommandId commandId);

  void finishForeignCommand();

  @Nullable ForeignEditorProvider getForeignEditorProvider(@Nullable Project project);

  static @Nullable UndoForeignCommandService getInstance() {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      return ProgressManager.getInstance().computeInNonCancelableSection(
        () -> application.getService(UndoForeignCommandService.class)
      );
    }
    return null;
  }

  static boolean isCommandInProgress() {
    UndoForeignCommandService service = getInstance();
    return service != null && service.getForeignEditorProvider(null) != null;
  }
}
