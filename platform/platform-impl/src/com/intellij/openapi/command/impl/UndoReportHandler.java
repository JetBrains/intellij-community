// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Internal
public interface UndoReportHandler {
  ExtensionPointName<UndoReportHandler> EP_NAME = ExtensionPointName.create("com.intellij.undoReportHandler");

  boolean reportNonUndoable(@Nullable Project project,
                            @Nls @NotNull String operationName,
                            @NotNull Collection<? extends DocumentReference> problemFiles,
                            boolean isUndo);

  boolean reportClashingDocuments(@Nullable Project project,
                                  @NotNull Collection<? extends DocumentReference> problemFiles,
                                  boolean isUndo);

  boolean reportCannotAdjust(@Nullable Project project,
                             @NotNull Collection<? extends DocumentReference> problemFiles,
                             boolean isUndo);

  boolean reportException(@Nullable Project project,
                          @NotNull UnexpectedUndoException exception,
                          boolean isUndo);

  boolean reportGeneric(@Nullable Project project,
                        @NlsContexts.DialogMessage String message,
                        @NotNull Collection<? extends DocumentReference> problemFiles);
}
