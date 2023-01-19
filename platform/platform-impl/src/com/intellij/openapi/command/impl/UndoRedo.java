// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

abstract class UndoRedo {
  protected final UndoManagerImpl myManager;
  protected final UndoManagerImpl.ClientState myState;
  protected final FileEditor myEditor;
  protected final UndoableGroup myUndoableGroup;

  protected UndoRedo(UndoManagerImpl.ClientState state, FileEditor editor) {
    myState = state;
    myManager = state.myManager;
    myEditor = editor;
    myUndoableGroup = getLastAction();
  }

  private UndoableGroup getLastAction() {
    return getStacksHolder().getLastAction(getDocRefs());
  }

  boolean isTransparent() {
    return myUndoableGroup.isTransparent();
  }

  boolean isTemporary() {
    return myUndoableGroup.isTemporary();
  }

  boolean hasMoreActions() {
    return getStacksHolder().canBeUndoneOrRedone(getDocRefs());
  }

  Collection<DocumentReference> getDocRefs() {
    return myEditor == null ? Collections.emptySet() : UndoManagerImpl.getDocumentReferences(myEditor);
  }

  protected abstract UndoRedoStacksHolder getStacksHolder();

  protected abstract UndoRedoStacksHolder getReverseStacksHolder();

  protected abstract SharedUndoRedoStacksHolder getSharedStacksHolder();

  protected abstract SharedUndoRedoStacksHolder getSharedReverseStacksHolder();

  @DialogTitle
  protected abstract String getActionName();

  @DialogMessage
  protected abstract String getActionName(String commandName);

  protected abstract EditorAndState getBeforeState();

  protected abstract EditorAndState getAfterState();

  protected abstract void performAction() throws UnexpectedUndoException;

  protected abstract void setBeforeState(EditorAndState state);

  public boolean execute(boolean drop, boolean disableConfirmation) {
    if (!myUndoableGroup.isUndoable()) {
      reportNonUndoable(myUndoableGroup.getAffectedDocuments());
      return false;
    }

    Set<DocumentReference> clashing = getStacksHolder().collectClashingActions(myUndoableGroup);
    if (!clashing.isEmpty()) {
      reportClashingDocuments(clashing);
      return false;
    }

    Map<DocumentReference, Set<ActionChangeRange>> reference2Ranges = decompose(myUndoableGroup, isRedo());
    SharedUndoRedoStacksHolder sharedStacksHolder = getSharedStacksHolder();
    boolean shouldMove = false;
    for (Map.Entry<DocumentReference, Set<ActionChangeRange>> entry : reference2Ranges.entrySet()) {
      MovementAvailability availability = sharedStacksHolder.canMoveToStackTop(entry.getKey(), entry.getValue());
      if (availability == MovementAvailability.CANNOT_MOVE) {
        reportCannotAdjust(Collections.singleton(entry.getKey()));
        return false;
      }
      if (availability == MovementAvailability.CAN_MOVE) {
        shouldMove = true;
      }
    }
    if (shouldMove) {
      for (Map.Entry<DocumentReference, Set<ActionChangeRange>> entry : reference2Ranges.entrySet()) {
        sharedStacksHolder.moveToStackTop(entry.getKey(), entry.getValue());
      }
    }

    if (!disableConfirmation && myUndoableGroup.shouldAskConfirmation(isRedo()) && !UndoManagerImpl.ourNeverAskUser) {
      if (!askUser()) return false;
    }
    else {
      if (!shouldMove && restore(getBeforeState(), true)) {
        setBeforeState(new EditorAndState(myEditor, myEditor.getState(FileEditorStateLevel.UNDO)));
        return true;
      }
    }

    Collection<VirtualFile> readOnlyFiles = collectReadOnlyAffectedFiles();
    if (!readOnlyFiles.isEmpty()) {
      final Project project = myManager.getProject();
      if (project == null) {
        return false;
      }

      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(readOnlyFiles);
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

    getStacksHolder().removeFromStacks(myUndoableGroup);
    if (!drop) {
      getReverseStacksHolder().addToStacks(myUndoableGroup);
    }

    SharedUndoRedoStacksHolder sharedReverseStacksHolder = getSharedReverseStacksHolder();
    for (Map.Entry<DocumentReference, Set<ActionChangeRange>> entry : reference2Ranges.entrySet()) {
      DocumentReference reference = entry.getKey();
      int rangeCount = entry.getValue().size();
      // All related ranges must be on the shared stack's top at this moment
      // so just pick them one by one and move to reverse stack
      for (int i = 0; i < rangeCount; i++) {
        ActionChangeRange changeRange = sharedStacksHolder.removeLastFromStack(reference);
        ActionChangeRange inverted = changeRange.asInverted();
        if (drop) {
          inverted = inverted.createIndependentCopy(true);
        }
        sharedReverseStacksHolder.addToStack(reference, inverted);
      }
    }

    try {
      performAction();
    } catch (UnexpectedUndoException e) {
      reportException(e);
      return false;
    }

    if (!shouldMove) {
      restore(getAfterState(), false);
    }

    return true;
  }

  private static Map<DocumentReference, Set<ActionChangeRange>> decompose(@NotNull UndoableGroup group, boolean isRedo) {
    Map<DocumentReference, Set<ActionChangeRange>> reference2Ranges = new HashMap<>();
    for (UndoableAction action : group.getActions()) {
      if (!(action instanceof AdjustableUndoableAction)) {
        continue;
      }
      AdjustableUndoableAction adjustable = (AdjustableUndoableAction)action;
      DocumentReference[] affected = adjustable.getAffectedDocuments();
      if (affected == null) {
        continue;
      }
      for (DocumentReference reference : affected) {
        Set<ActionChangeRange> savedChangeRanges = reference2Ranges.computeIfAbsent(reference, r -> new HashSet<>());
        for (ActionChangeRange changeRange : adjustable.getChangeRanges(reference)) {
          savedChangeRanges.add(isRedo ? changeRange.asInverted() : changeRange);
        }
      }
    }
    return reference2Ranges;
  }

  protected abstract boolean isRedo();

  private Collection<Document> collectReadOnlyDocuments() {
    Collection<Document> readOnlyDocs = new ArrayList<>();
    for (UndoableAction action : myUndoableGroup.getActions()) {
      if (action instanceof MentionOnlyUndoableAction) continue;

      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) continue;

      for (DocumentReference ref : refs) {
        if (ref instanceof DocumentReferenceByDocument docRef) {
          Document doc = docRef.getDocument();
          if (!doc.isWritable()) readOnlyDocs.add(doc);
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

  private void reportNonUndoable(@NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportNonUndoable(myManager.getProject(), problemFiles, !isRedo()));
  }

  private void reportClashingDocuments(@NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportClashingDocuments(myManager.getProject(), problemFiles, !isRedo()));
  }

  private void reportCannotAdjust(@NotNull Collection<? extends DocumentReference> problemFiles) {
    doWithReportHandler(handler -> handler.reportCannotAdjust(myManager.getProject(), problemFiles, !isRedo()));
  }

  private void reportException(@NotNull UnexpectedUndoException e) {
    doWithReportHandler(handler -> handler.reportException(myManager.getProject(), e, !isRedo()));
  }

  private static void doWithReportHandler(Predicate<? super UndoReportHandler> condition) {
    for (var handler : UndoReportHandler.EP_NAME.getExtensionList()) {
      if (condition.test(handler)) {
        return;
      }
    }
  }

  private boolean askUser() {
    String actionText = getActionName(myUndoableGroup.getCommandName());
    return Messages.showOkCancelDialog(myManager.getProject(), actionText + "?", getActionName(),
                                          Messages.getQuestionIcon()) == Messages.OK;
  }

  boolean confirmSwitchTo(@NotNull UndoRedo other) {
    String message = IdeBundle.message("undo.conflicting.change.confirmation") + "\n" +
                     getActionName(other.myUndoableGroup.getCommandName()) + "?";
    return Messages.showOkCancelDialog(myManager.getProject(), message, getActionName(),
                                          Messages.getQuestionIcon()) == Messages.OK;
  }

  private boolean restore(EditorAndState pair, boolean onlyIfDiffers) {
    // editor can be invalid if underlying file is deleted during undo (e.g. after undoing scratch file creation)
    if (pair == null || myEditor == null || !myEditor.isValid() || !pair.canBeAppliedTo(myEditor)) return false;

    FileEditorState stateToRestore = pair.getState();
    // If current editor state isn't equals to remembered state then
    // we have to try to restore previous state. But sometime it's
    // not possible to restore it. For example, it's not possible to
    // restore scroll proportion if editor doesn not have scrolling any more.
    FileEditorState currentState = myEditor.getState(FileEditorStateLevel.UNDO);
    if (onlyIfDiffers && currentState.equals(stateToRestore)) {
      return false;
    }

    myEditor.setState(stateToRestore);
    FileEditorState newState = myEditor.getState(FileEditorStateLevel.UNDO);
    return newState.equals(stateToRestore);
  }

  public boolean isBlockedByOtherChanges() {
    return myUndoableGroup.isGlobal() && myUndoableGroup.isUndoable() &&
           !getStacksHolder().collectClashingActions(myUndoableGroup).isEmpty();
  }
}