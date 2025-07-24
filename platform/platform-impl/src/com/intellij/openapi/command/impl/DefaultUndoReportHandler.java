// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

final class DefaultUndoReportHandler implements UndoReportHandler {
  private static final Logger LOG = Logger.getInstance(DefaultUndoReportHandler.class);

  @Override
  public boolean reportNonUndoable(@Nullable Project project,
                                   @Nls @NotNull String operationName,
                                   @NotNull Collection<? extends DocumentReference> problemFiles,
                                   boolean isUndo) {
    String message = IdeBundle.message("cannot.undo.error.contains.nonundoable.changes.message", operationName);
    return reportGeneric(project, message, problemFiles);
  }

  @Override
  public boolean reportClashingDocuments(@Nullable Project project,
                                         @NotNull Collection<? extends DocumentReference> problemFiles,
                                         boolean isUndo) {
    return reportGeneric(project, IdeBundle.message("cannot.undo.error.other.affected.files.changed.message"), problemFiles);
  }

  @Override
  public boolean reportCannotAdjust(@Nullable Project project,
                                    @NotNull Collection<? extends DocumentReference> problemFiles,
                                    boolean isUndo) {
    return reportGeneric(project, IdeBundle.message("cannot.undo.error.other.users.overwrote.changes.message"), problemFiles);
  }

  @Override
  public boolean reportException(@Nullable Project project,
                                 @NotNull UnexpectedUndoException exception,
                                 boolean isUndo) {
    String title;
    String message;

    if (isUndo) {
      title = IdeBundle.message("cannot.undo.title");
      message = IdeBundle.message("cannot.undo.message");
    }
    else {
      title = IdeBundle.message("cannot.redo.title");
      message = IdeBundle.message("cannot.redo.message");
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (exception.getMessage() != null) {
        message += ".\n" + exception.getMessage();
      }
      Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
    }
    else {
      LOG.error(exception);
    }
    return true;
  }

  @Override
  public boolean reportGeneric(@Nullable Project project,
                               @NlsContexts.DialogMessage String message,
                               @NotNull Collection<? extends DocumentReference> problemFiles) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(message + "\n" + StringUtil.join(problemFiles, "\n"));
    }
    new CannotUndoReportDialog(project, message, problemFiles).show();
    return true;
  }
}
