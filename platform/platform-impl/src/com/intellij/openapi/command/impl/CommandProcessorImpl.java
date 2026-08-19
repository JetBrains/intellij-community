// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;


@ApiStatus.Internal
public final class CommandProcessorImpl extends CoreCommandProcessor implements Disposable {

  @Override
  public void finishCommand(@NotNull CommandToken command, @Nullable Throwable throwable) {
    if (!isCommandTokenActive(command)) {
      return;
    }
    boolean isPCE = throwable instanceof ProcessCanceledException;
    try {
      if (throwable != null && !isPCE) {
        ExceptionUtil.rethrowUnchecked(throwable);
        LOG.error(throwable);
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
    if (throwable != null) {
      boolean showTooComplexDialog = !isPCE; // IJPL-1116 Cancellation causes "Too complex" message
      undoLastOperation(command, showTooComplexDialog);
    }
  }

  @Override
  public void markCurrentCommandAsGlobal(@Nullable Project project) {
    var undoManagerImpl = getUndoManagerImpl(project);
    if (undoManagerImpl != null) {
      undoManagerImpl.markCurrentCommandAsGlobal();
    }
  }

  @Override
  public void dispose() {
    // [analyzer] IJPL-199712: Dispose command processor between executions
  }

  @Override
  public void addAffectedDocuments(@Nullable Project project, Document @NotNull ... docs) {
    var undoManagerImpl = getUndoManagerImpl(project);
    if (undoManagerImpl != null) {
      undoManagerImpl.addAffectedDocuments(docs);
    }
  }

  @Override
  public void addAffectedFiles(@Nullable Project project, VirtualFile @NotNull ... files) {
    var undoManagerImpl = getUndoManagerImpl(project);
    if (undoManagerImpl != null) {
      undoManagerImpl.addAffectedFiles(files);
    }
  }

  private static void undoLastOperation(@NonNull CommandToken command, boolean showTooComplexDialog) {
    Project project = command.getProject();
    if (project != null) {
      var undoManagerImpl = getUndoManagerImpl(project);
      if (undoManagerImpl != null) {
        FileEditor editor = undoManagerImpl.getEditorProvider().getCurrentEditor(project);

        if (undoManagerImpl.isUndoAvailable(editor)) {
          undoManagerImpl.undo(editor);
        }
      }
    }
    if (showTooComplexDialog) {
      Messages.showErrorDialog(
        project,
        IdeBundle.message("dialog.message.cannot.perform.operation.too.complex.sorry"),
        IdeBundle.message("dialog.title.failed.to.perform.operation")
      );
    }
  }

  /**
   * Note: since not every implementation of {@link UndoManager} is actually an {@link UndoManagerImpl},
   * this method returns null in cases when this is not the case. Callers have to handle that possibility.
   */
  private static @Nullable UndoManagerImpl getUndoManagerImpl(@Nullable Project project) {
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();

    if (undoManager instanceof UndoManagerImpl impl) {
      return impl;
    }

    LOG.debug(UndoManager.class.getSimpleName() + " is not an instance of " +
              UndoManagerImpl.class.getSimpleName() + ", instead it was '" +
              undoManager.getClass().getCanonicalName() + "'. " +
              CommandProcessorImpl.class.getSimpleName() + "'s functionality can be affected by this.");
    return null;
  }
}
