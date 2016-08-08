/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
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

  boolean hasMoreActions() {
    return getStackHolder().canBeUndoneOrRedone(getDecRefs());
  }

  private Set<DocumentReference> getDecRefs() {
    return myEditor == null ? Collections.<DocumentReference>emptySet() : UndoManagerImpl.getDocumentReferences(myEditor);
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


    if (!isInsideStartFinishGroup && myUndoableGroup.shouldAskConfirmation(isRedo())) {
      if (!askUser()) return false;
    }
    else {
      if (restore(getBeforeState())) {
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

    restore(getAfterState());

    return true;
  }

  protected abstract boolean isRedo();

  private Collection<Document> collectReadOnlyDocuments() {
    Collection<DocumentReference> affectedDocument = myUndoableGroup.getAffectedDocuments();
    Collection<Document> readOnlyDocs = new ArrayList<>();
    for (DocumentReference ref : affectedDocument) {
      if (ref instanceof DocumentReferenceByDocument) {
        Document doc = ref.getDocument();
        if (doc != null && !doc.isWritable()) readOnlyDocs.add(doc);
      }
    }
    return readOnlyDocs;
  }

  private Collection<VirtualFile> collectReadOnlyAffectedFiles() {
    Collection<DocumentReference> affectedDocument = myUndoableGroup.getAffectedDocuments();
    Collection<VirtualFile> readOnlyFiles = new ArrayList<>();
    for (DocumentReference documentReference : affectedDocument) {
      VirtualFile file = documentReference.getFile();
      if ((file != null) && file.isValid() && !file.isWritable()) {
        readOnlyFiles.add(file);
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
    String actionText = getActionName(myUndoableGroup.getCommandName());

    if (actionText.length() > 80) {
      actionText = actionText.substring(0, 80) + "... ";
    }

    return Messages.showOkCancelDialog(myManager.getProject(), actionText + "?", getActionName(),
                                       Messages.getQuestionIcon()) == Messages.OK;
  }

  private boolean restore(EditorAndState pair) {
    if (myEditor == null ||
        !myEditor.isValid() || // editor can be invalid if underlying file is deleted during undo (e.g. after undoing scratch file creation)
        pair == null || pair.getEditor() == null) {
      return false;
    }

    // we cannot simply compare editors here because of the following scenario:
    // 1. make changes in editor for file A
    // 2. move caret
    // 3. close editor
    // 4. re-open editor for A via Ctrl-E
    // 5. undo -> position is not affected, because instance created in step 4 is not the same!!!
    if (!myEditor.getClass().equals(pair.getEditor().getClass())) {
      return false;
    }

    // If current editor state isn't equals to remembered state then
    // we have to try to restore previous state. But sometime it's
    // not possible to restore it. For example, it's not possible to
    // restore scroll proportion if editor doesn not have scrolling any more.
    FileEditorState currentState = myEditor.getState(FileEditorStateLevel.UNDO);
    if (currentState.equals(pair.getState())) {
      return false;
    }

    myEditor.setState(pair.getState());
    return true;
  }
}
