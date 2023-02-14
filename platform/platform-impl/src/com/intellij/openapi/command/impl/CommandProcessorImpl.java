// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.FocusBasedCurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CommandProcessorImpl extends CoreCommandProcessor {
  @Override
  public void finishCommand(@NotNull final CommandToken command, @Nullable final Throwable throwable) {
    if (myCurrentCommand != command) return;
    final boolean failed;
    try {
      if (throwable != null) {
        failed = true;
        ExceptionUtil.rethrowUnchecked(throwable);
        CommandLog.LOG.error(throwable);
      }
      else {
        failed = false;
      }
    }
    finally {
      try {
        super.finishCommand(command, throwable);
      }
      catch (Throwable e) {
        if (throwable != null && throwable != e) {
          e.addSuppressed(throwable);
        }
        throw e;
      }
    }
    if (failed) {
      Project project = command.getProject();
      if (project != null) {
        FileEditor editor = new FocusBasedCurrentEditorProvider().getCurrentEditor();
        final UndoManager undoManager = UndoManager.getInstance(project);
        if (undoManager.isUndoAvailable(editor)) {
          undoManager.undo(editor);
        }
      }
      Messages.showErrorDialog(project, IdeBundle.message("dialog.message.cannot.perform.operation.too.complex.sorry"),
                               IdeBundle.message("dialog.title.failed.to.perform.operation"));
    }
  }

  @Override
  public void markCurrentCommandAsGlobal(Project project) {
    getUndoManager(project).markCurrentCommandAsGlobal();
  }

  private static UndoManagerImpl getUndoManager(Project project) {
    return (UndoManagerImpl)(project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance());
  }

  @Override
  public void addAffectedDocuments(Project project, Document @NotNull ... docs) {
    getUndoManager(project).addAffectedDocuments(docs);
  }

  @Override
  public void addAffectedFiles(Project project, VirtualFile @NotNull ... files) {
    getUndoManager(project).addAffectedFiles(files);
  }
}
