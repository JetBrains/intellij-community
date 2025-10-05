// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;


final class UndoProblemReport {
  private final @Nullable Project project;
  private final boolean isUndo;

  UndoProblemReport(@Nullable Project project, boolean isUndo) {
    this.project = project;
    this.isUndo = isUndo;
  }

  void reportNonUndoable(@Nls @NotNull String operationName, @NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportNonUndoable(project, operationName, problemFiles, isUndo));
  }

  void reportClashingDocuments(@NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportClashingDocuments(project, problemFiles, isUndo));
  }

  void reportCannotAdjust(@NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportCannotAdjust(project, problemFiles, isUndo));
  }

  void reportException(@NotNull UnexpectedUndoException e) {
    doWithReportHandler(handler -> handler.reportException(project, e, isUndo));
  }

  private static void doWithReportHandler(@NotNull Predicate<? super UndoReportHandler> condition) {
    for (var handler : UndoReportHandler.EP_NAME.getExtensionList()) {
      if (condition.test(handler)) {
        return;
      }
    }
  }
}
