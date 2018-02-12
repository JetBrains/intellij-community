// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

abstract class UndoRedo {
  protected final UndoManagerImpl myManager;
  protected final FileEditor myEditor;
  protected final UndoableGroup myUndoableGroup;

  //public static void execute(UndoManagerImpl manager, FileEditor editor, boolean isUndo) {
  //  do {
  //    UndoRedo undoOrRedo = isUndo ? new Undo(manager, editor) : new Redo(manager, editor);
  //    undoOrRedo.doExecute();
  //    boolean shouldRepeat = undoOrRedo.isTransparent() && undoOrRedo.hasMoreActions();
  //    if (!shouldRepeat) break;
  //  }
  //  while (true);
  //}
  //
  protected UndoRedo(UndoManagerImpl manager, FileEditor editor) {
    myManager = manager;
    myEditor = editor;
    myUndoableGroup = getLastAction();
  }

  private UndoableGroup getLastAction() {
    return getStackHolder().getLastAction(getDecRefs());
  }

  boolean isTransparent() {
    return myUndoableGroup.isTransparent();
  }

  boolean isTemporary() {
    return myUndoableGroup.isTemporary();
  }

  boolean hasMoreActions() {
    return getStackHolder().canBeUndoneOrRedone(getDecRefs());
  }

  private Set<DocumentReference> getDecRefs() {
    return myEditor == null ? Collections.emptySet() : UndoManagerImpl.getDocumentReferences(myEditor);
  }

  protected abstract UndoRedoStacksHolder getStackHolder();

  protected abstract UndoRedoStacksHolder getReverseStackHolder();

  protected abstract String getActionName();

  protected abstract String getActionName(String commandName);

  protected abstract EditorAndState getBeforeState();

  protected abstract EditorAndState getAfterState();

  protected abstract void performAction();

  protected abstract void setBeforeState(EditorAndState state);

  public boolean execute(boolean drop, boolean isInsideStartFinishGroup) {
    if (!myUndoableGroup.isUndoable()) {
      reportCannotUndo(CommonBundle.message("cannot.undo.error.contains.nonundoable.changes.message"),
                       myUndoableGroup.getAffectedDocuments());
      return false;
    }

    Set<DocumentReference> clashing = getStackHolder().collectClashingActions(myUndoableGroup);
    if (!clashing.isEmpty()) {
      reportCannotUndo(CommonBundle.message("cannot.undo.error.other.affected.files.changed.message"), clashing);
      return false;
    }


    if (!isInsideStartFinishGroup && myUndoableGroup.shouldAskConfirmation(isRedo()) && !UndoManagerImpl.ourNeverAskUser) {
      if (!askUser()) return false;
    }
    else {
      if (restore(getBeforeState(), true)) {
        setBeforeState(new EditorAndState(myEditor, myEditor.getState(FileEditorStateLevel.UNDO)));
        return true;
      }
    }

    Collection<VirtualFile> readOnlyFiles = collectReadOnlyAffectedFiles();
    if (!readOnlyFiles.isEmpty()) {
      final Project project = myManager.getProject();
      final VirtualFile[] files = VfsUtil.toVirtualFileArray(readOnlyFiles);

      if (project == null) {
        return false;
      }

      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
      if (operationStatus.hasReadonlyFiles()) {
        return false;
      }
    }

    Collection<Document> readOnlyDocuments = collectReadOnlyDocuments();
    if (!readOnlyDocuments.isEmpty()) {
      for (Document document : readOnlyDocuments) {
        document.fireReadOnlyModificationAttempt();
      }
      return false;
    }

    getStackHolder().removeFromStacks(myUndoableGroup);
    if (!drop) {
      getReverseStackHolder().addToStacks(myUndoableGroup);
    }

    performAction();

    restore(getAfterState(), false);

    return true;
  }

  protected abstract boolean isRedo();

  private Collection<Document> collectReadOnlyDocuments() {
    Collection<Document> readOnlyDocs = new ArrayList<>();
    for (UndoableAction action : myUndoableGroup.getActions()) {
      if (action instanceof MentionOnlyUndoableAction) continue;

      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) continue;

      for (DocumentReference ref : refs) {
        if (ref instanceof DocumentReferenceByDocument) {
          Document doc = ref.getDocument();
          if (doc != null && !doc.isWritable()) readOnlyDocs.add(doc);
        }
      }
    }
    return readOnlyDocs;
  }

  private Collection<VirtualFile> collectReadOnlyAffectedFiles() {
    Collection<VirtualFile> readOnlyFiles = new ArrayList<>();
    for (UndoableAction action : myUndoableGroup.getActions()) {
      if (action instanceof MentionOnlyUndoableAction) continue;

      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) continue;

      for (DocumentReference ref : refs) {
        VirtualFile file = ref.getFile();
        if ((file != null) && file.isValid() && !file.isWritable()) {
          readOnlyFiles.add(file);
        }
      }
    }
    return readOnlyFiles;
  }

  private void reportCannotUndo(String message, Collection<DocumentReference> problemFiles) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(
        message + "\n" + StringUtil.join(problemFiles, StringUtil.createToStringFunction(DocumentReference.class), "\n"));
    }
    new CannotUndoReportDialog(myManager.getProject(), message, problemFiles).show();
  }

  private boolean askUser() {
    final boolean[] isOk = new boolean[1];
    TransactionGuard.getInstance().submitTransactionAndWait(() -> {
      String actionText = getActionName(myUndoableGroup.getCommandName());

      if (actionText.length() > 80) {
        actionText = actionText.substring(0, 80) + "... ";
      }

      isOk[0] = Messages.showOkCancelDialog(myManager.getProject(), actionText + "?", getActionName(),
                                            Messages.getQuestionIcon()) == Messages.OK;
    });
    return isOk[0];
  }

  private boolean restore(EditorAndState pair, boolean onlyIfDiffers) {
    // editor can be invalid if underlying file is deleted during undo (e.g. after undoing scratch file creation)
    if (pair == null || myEditor == null || !myEditor.isValid() || !pair.canBeAppliedTo(myEditor)) return false;
    
    // If current editor state isn't equals to remembered state then
    // we have to try to restore previous state. But sometime it's
    // not possible to restore it. For example, it's not possible to
    // restore scroll proportion if editor doesn not have scrolling any more.
    FileEditorState currentState = myEditor.getState(FileEditorStateLevel.UNDO);
    if (onlyIfDiffers && currentState.equals(pair.getState())) {
      return false;
    }

    myEditor.setState(pair.getState());
    return true;
  }
}
