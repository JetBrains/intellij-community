// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.AbstractFileViewProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DocumentUndoProvider implements DocumentListener {
  private static final Key<Boolean> UNDOING_EDITOR_CHANGE = Key.create("DocumentUndoProvider.UNDOING_EDITOR_CHANGE");

  private DocumentUndoProvider() {
  }

  private static @NotNull UndoManagerImpl getUndoManager(@Nullable Project project) {
    return (UndoManagerImpl)(project == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(project));
  }

  public static void startDocumentUndo(@Nullable Document doc) {
    if (doc != null) doc.putUserData(UNDOING_EDITOR_CHANGE, Boolean.TRUE);
  }

  public static void finishDocumentUndo(@Nullable Document doc) {
    if (doc != null) doc.putUserData(UNDOING_EDITOR_CHANGE, null);
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent e) {
    Document document = e.getDocument();
    if (!shouldProcess(document)) {
      return;
    }

    handleBeforeDocumentChange(getUndoManager(null), document);

    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager != null) {
      for (Project project : projectManager.getOpenProjects()) {
        handleBeforeDocumentChange(getUndoManager(project), document);
      }
    }
    Project lightEditProject = LightEditUtil.getProjectIfCreated();
    if (lightEditProject != null) {
      handleBeforeDocumentChange(getUndoManager(lightEditProject), document);
    }
  }

  private static void handleBeforeDocumentChange(@NotNull UndoManagerImpl undoManager, @NotNull Document document) {
    if (undoManager.isActive() && isUndoable(undoManager, document) && undoManager.isUndoOrRedoInProgress() &&
        document.getUserData(UNDOING_EDITOR_CHANGE) != Boolean.TRUE) {
      throw new IllegalStateException("Do not change documents during undo as it will break undo sequence.");
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    Document document = e.getDocument();
    if (!shouldProcess(document)) {
      return;
    }

    handleDocumentChanged(getUndoManager(null), document, e);
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager != null) {
      for (Project project : projectManager.getOpenProjects()) {
        handleDocumentChanged(getUndoManager(project), document, e);
      }
    }
    Project lightEditProject = LightEditUtil.getProjectIfCreated();
    if (lightEditProject != null) {
      handleDocumentChanged(getUndoManager(lightEditProject), document, e);
    }
  }

  private static void handleDocumentChanged(@NotNull UndoManagerImpl undoManager, @NotNull Document document, @NotNull DocumentEvent e) {
    if (undoManager.isActive() && isUndoable(undoManager, document)) {
      registerUndoableAction(undoManager, e);
    }
    else {
      registerNonUndoableAction(undoManager, document);
    }
  }

  private static boolean shouldProcess(@NotNull Document document) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      // some light document
      return false;
    }

    return !UndoDocumentUtil.isCopy(document) // if we don't ignore copy's events, we will receive notification
           // for the same event twice (from original document too)
           // and undo will work incorrectly
           && shouldRecordActions(document);
  }

  private static boolean shouldRecordActions(@NotNull Document document) {
    if (UndoUtil.isUndoDisabledFor(document)) return false;

    VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    if (vFile == null) return true;
    return vFile.getUserData(AbstractFileViewProvider.FREE_THREADED) != Boolean.TRUE &&
           !UndoUtil.isUndoDisabledFor(vFile);
  }

  private static void registerUndoableAction(@NotNull UndoManagerImpl undoManager, @NotNull DocumentEvent e) {
    undoManager.undoableActionPerformed(new EditorChangeAction(e));
  }

  private static void registerNonUndoableAction(@NotNull UndoManagerImpl undoManager, @NotNull Document document) {
    DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
    undoManager.nonundoableActionPerformed(ref, false);
  }

  private static boolean isUndoable(@NotNull UndoManagerImpl undoManager, @NotNull Document document) {
    DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
    VirtualFile file = ref.getFile();

    // Allow undo even from refresh if requested
    if (file != null && UndoUtil.isForceUndoFlagSet(file)) {
      return true;
    }
    return !UndoManagerImpl.isRefresh() ||
           undoManager.isUndoRedoAvailable(ref, true) ||
           undoManager.isUndoRedoAvailable(ref, false);
  }
}
