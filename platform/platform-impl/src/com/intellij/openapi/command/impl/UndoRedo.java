// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


abstract class UndoRedo {
  private final @Nullable Project project;
  private final @Nullable FileEditor editor;
  private final @NotNull UndoRedoStacksHolder stacksHolder;
  private final @NotNull UndoRedoStacksHolder stacksHolderReversed;
  private final @NotNull SharedUndoRedoStacksHolder sharedStacksHolder;
  private final @NotNull SharedUndoRedoStacksHolder sharedStacksHolderReversed;
  private final @NotNull UndoProblemReport undoProblemReport;
  protected final @NotNull UndoableGroup undoableGroup;
  private final boolean isRedo;

  protected UndoRedo(
    @Nullable Project project,
    @Nullable FileEditor editor,
    @NotNull UndoRedoStacksHolder stacksHolder,
    @NotNull UndoRedoStacksHolder stacksHolderReversed,
    @NotNull SharedUndoRedoStacksHolder sharedStacksHolder,
    @NotNull SharedUndoRedoStacksHolder sharedStacksHolderReversed,
    boolean isRedo
  ) {
    this.project = project;
    this.editor = editor;
    this.stacksHolder = stacksHolder;
    this.stacksHolderReversed = stacksHolderReversed;
    this.sharedStacksHolder = sharedStacksHolder;
    this.sharedStacksHolderReversed = sharedStacksHolderReversed;
    this.isRedo = isRedo;
    this.undoProblemReport = new UndoProblemReport(project, isRedo);
    this.undoableGroup = stacksHolder.getLastAction(getDocRefs());
  }

  protected abstract @DialogTitle String getActionName();

  protected abstract @DialogMessage String getActionName(String commandName);

  protected abstract @Nullable EditorAndState getBeforeState();

  protected abstract @Nullable EditorAndState getAfterState();

  protected abstract void performAction() throws UnexpectedUndoException;

  protected abstract void setBeforeState(@NotNull EditorAndState state);

  boolean isGlobal() {
    return undoableGroup.isGlobal();
  }

  boolean isTransparent() {
    return undoableGroup.isTransparent();
  }

  boolean isTemporary() {
    return undoableGroup.isTemporary();
  }

  boolean hasMoreActions() {
    return stacksHolder.canBeUndoneOrRedone(getDocRefs());
  }

  boolean execute(boolean drop, boolean disableConfirmation) {
    if (!undoableGroup.isUndoable()) {
      String operationName = Objects.requireNonNull(
        CommandProcessor.getInstance().getCurrentCommandName(),
        "performing undo/redo operation outside command context"
      );
      undoProblemReport.reportNonUndoable(operationName, undoableGroup.getAffectedDocuments());
      return false;
    }

    Set<DocumentReference> clashing = stacksHolder.collectClashingActions(undoableGroup);
    if (!clashing.isEmpty()) {
      undoProblemReport.reportClashingDocuments(clashing);
      return false;
    }

    Map<DocumentReference, Map<Integer, MutableActionChangeRange>> reference2Ranges = decompose(undoableGroup, isRedo);
    boolean shouldMove = false;
    for (Map.Entry<DocumentReference, Map<Integer, MutableActionChangeRange>> entry : reference2Ranges.entrySet()) {
      MovementAvailability availability = sharedStacksHolder.canMoveToStackTop(entry.getKey(), entry.getValue());
      if (availability == MovementAvailability.CANNOT_MOVE) {
        undoProblemReport.reportCannotAdjust(Collections.singleton(entry.getKey()));
        return false;
      }
      if (availability == MovementAvailability.CAN_MOVE) {
        shouldMove = true;
      }
    }

    if (!disableConfirmation && undoableGroup.shouldAskConfirmation(isRedo) && !isNeverAskUser()) {
      if (!askUser()) {
        return false;
      }
    }
    else {
      if (!shouldMove && editor != null && restore(getBeforeState(), true)) {
        setBeforeState(new EditorAndState(editor, editor.getState(FileEditorStateLevel.UNDO)));
        if (!isCaretMovementUndoTransparent()) {
          return true;
        }
      }
    }

    Collection<VirtualFile> readOnlyFiles = UndoDocumentUtil.collectReadOnlyAffectedFiles(undoableGroup.getActions());
    if (!readOnlyFiles.isEmpty()) {
      if (project == null) {
        return false;
      }
      ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(readOnlyFiles);
      if (operationStatus.hasReadonlyFiles()) {
        return false;
      }
    }

    Collection<Document> readOnlyDocuments = UndoDocumentUtil.collectReadOnlyDocuments(undoableGroup.getActions());
    if (!readOnlyDocuments.isEmpty()) {
      for (Document document : readOnlyDocuments) {
        document.fireReadOnlyModificationAttempt();
      }
      return false;
    }

    if (shouldMove) {
      for (Map.Entry<DocumentReference, Map<Integer, MutableActionChangeRange>> entry : reference2Ranges.entrySet()) {
        var affected = sharedStacksHolder.moveToStackTop(entry.getKey(), entry.getValue());
        if (affected != null) {
          for (ImmutableActionChangeRange range : affected) {
            MutableActionChangeRange mutableRange = entry.getValue().get(range.getId());
            if (mutableRange != null) {
              mutableRange.setState(range);
            }
          }
        }
      }
    }

    stacksHolder.removeFromStacks(undoableGroup);
    if (!drop) {
      stacksHolderReversed.addToStacks(undoableGroup);
    }

    for (Map.Entry<DocumentReference, Map<Integer, MutableActionChangeRange>> entry : reference2Ranges.entrySet()) {
      DocumentReference reference = entry.getKey();
      int rangeCount = entry.getValue().size();
      // All related ranges must be on the shared stack's top at this moment
      // so just pick them one by one and move to reverse stack
      for (int i = 0; i < rangeCount; i++) {
        ImmutableActionChangeRange changeRange = sharedStacksHolder.removeLastFromStack(reference);
        ImmutableActionChangeRange inverted = changeRange.asInverted().toImmutable(drop);
        sharedStacksHolderReversed.addToStack(reference, inverted);
      }
    }

    try {
      performAction();
    } catch (UnexpectedUndoException e) {
      undoProblemReport.reportException(e);
      return false;
    }

    if (!shouldMove) {
      restore(getAfterState(), false);
    }

    return true;
  }

  boolean isInsideStartFinishGroup(boolean isInsideStartFinishGroup) {
    return undoableGroup.isInsideStartFinishGroup(!isRedo, isInsideStartFinishGroup);
  }

  boolean isBlockedByOtherChanges() {
    return undoableGroup.isGlobal() &&
           undoableGroup.isUndoable() &&
           !stacksHolder.collectClashingActions(undoableGroup).isEmpty();
  }

  /**
   * In case of global group blocking undo we can perform undo locally and separate undone changes from others stacks
   */
  boolean splitGlobalCommand() {
    if (isRedo) {
      throw new IllegalStateException("splitGlobalCommand is allowed only for Undo but current operation is Redo");
    }
    Collection<DocumentReference> refs = getDocRefs();
    if (refs == null || refs.size() != 1) {
      return false;
    }
    DocumentReference docRef = refs.iterator().next();
    UndoRedoList<UndoableGroup> stack = stacksHolder.getStack(docRef);
    if (stack.getLast() == undoableGroup) {
      var actions = UndoDocumentUtil.separateLocalAndNonLocalActions(
        undoableGroup.getActions(),
        docRef
      );
      List<UndoableAction> localActions = actions.getFirst();
      List<UndoableAction> nonLocalActions = actions.getSecond();
      if (localActions.isEmpty()) {
        return false;
      }
      stack.removeLast();
      UndoableGroup replacingGroup = new UndoableGroup(
        project,
        IdeBundle.message("undo.command.local.name") + undoableGroup.getCommandName(),
        localActions, // only action that changes file locally
        undoableGroup.getConfirmationPolicy(),
        stacksHolder,
        undoableGroup.getStateBefore(),
        undoableGroup.getStateAfter(),
        null,
        undoableGroup.getCommandTimestamp(),
        undoableGroup.isTransparent(),
        false,
        undoableGroup.isValid()
      );
      stack.add(replacingGroup);
      UndoableGroup groupWithoutLocalChanges = new UndoableGroup(
        project,
        undoableGroup.getCommandName(),
        nonLocalActions, // all action except local
        undoableGroup.getConfirmationPolicy(),
        stacksHolder,
        undoableGroup.getStateBefore(),
        undoableGroup.getStateAfter(),
        null,
        undoableGroup.getCommandTimestamp(),
        undoableGroup.isTransparent(),
        undoableGroup.isGlobal(),
        undoableGroup.isValid()
      );
      if (stacksHolder.replaceOnStacks(undoableGroup, groupWithoutLocalChanges)) {
        replacingGroup.setOriginalContext(new UndoableGroupOriginalContext(
          undoableGroup,
          groupWithoutLocalChanges
        ));
      }
      return true;
    }
    return false;
  }

  /**
   * If we redo group that was split before, we gather that group into global command(as it was before splitting)
   * and recover that command on all stacks
   */
  void gatherGlobalCommand() {
    if (!isRedo) {
      throw new IllegalStateException("gatherGlobalCommand is allowed only for Redo but current operation is Undo");
    }
    UndoableGroupOriginalContext context = undoableGroup.getOriginalContext();
    if (context == null) {
      return;
    }
    Collection<DocumentReference> refs = getDocRefs();
    if (refs.size() > 1) {
      return;
    }
    DocumentReference docRef = refs.iterator().next();
    UndoRedoStacksHolder undoStacksHolder = stacksHolderReversed;
    UndoRedoList<UndoableGroup> undoStack = undoStacksHolder.getStack(docRef);
    if (undoStack.getLast() != undoableGroup) {
      return;
    }
    boolean shouldGatherGroup = undoStacksHolder.replaceOnStacks(context.currentStackGroup(), context.originalGroup());
    if (!shouldGatherGroup) {
      return;
    }
    undoStack.removeLast();
    undoStack.add(context.originalGroup());
  }

  boolean isSameUndoableGroup(@NotNull UndoRedo otherUndoRedo) {
    return undoableGroup == otherUndoRedo.undoableGroup;
  }

  boolean confirmSwitchTo(@NotNull UndoRedo other) {
    String message = IdeBundle.message("undo.conflicting.change.confirmation") + "\n" + getActionName(other.undoableGroup.getCommandName()) + "?";
    return showDialog(message);
  }

  private boolean askUser() {
    return showDialog(getActionName(undoableGroup.getCommandName()) + "?");
  }

  private boolean showDialog(@DialogMessage @NotNull String message) {
    return Messages.OK == Messages.showOkCancelDialog(project, message, getActionName(), Messages.getQuestionIcon());
  }

  private static boolean isNeverAskUser() {
    //noinspection TestOnlyProblems
    return UndoManagerImpl.ourNeverAskUser;
  }

  private boolean restore(@Nullable EditorAndState pair, boolean onlyIfDiffers) {
    // editor can be invalid if underlying file is deleted during undo (e.g. after undoing scratch file creation)
    if (pair == null || editor == null || !editor.isValid() || !pair.canBeAppliedTo(editor)) {
      return false;
    }

    FileEditorState stateToRestore = pair.getState();
    // If current editor state isn't equals to remembered state then
    // we have to try to restore previous state. But sometime it's
    // not possible to restore it. For example, it's not possible to
    // restore scroll proportion if editor doesn not have scrolling any more.
    FileEditorState currentState = editor.getState(FileEditorStateLevel.UNDO);
    if (onlyIfDiffers && currentState.equals(stateToRestore)) {
      return false;
    }

    editor.setState(stateToRestore);
    FileEditorState newState = editor.getState(FileEditorStateLevel.UNDO);
    return newState.equals(stateToRestore);
  }

  private Collection<DocumentReference> getDocRefs() {
    return editor == null ? Collections.emptySet() : UndoDocumentUtil.getDocumentReferences(editor);
  }

  private static @NotNull Map<DocumentReference, Map<Integer, MutableActionChangeRange>> decompose(@NotNull UndoableGroup group, boolean isRedo) {
    Map<DocumentReference, Map<Integer, MutableActionChangeRange>> reference2Ranges = new HashMap<>();
    for (UndoableAction action : group.getActions()) {
      if (!(action instanceof AdjustableUndoableAction adjustable)) {
        continue;
      }
      DocumentReference[] affected = adjustable.getAffectedDocuments();
      if (affected == null) {
        continue;
      }
      for (DocumentReference reference : affected) {
        Map<Integer, MutableActionChangeRange> savedChangeRanges = reference2Ranges.computeIfAbsent(reference, r -> new HashMap<>());
        for (MutableActionChangeRange changeRange : adjustable.getChangeRanges(reference)) {
          MutableActionChangeRange range = isRedo ? changeRange.asInverted() : changeRange;
          savedChangeRanges.put(range.getId(), range);
        }
      }
    }
    return reference2Ranges;
  }

  /**
   * Returns {@code true} if caret movement is not a separate undo step, see IJPL-28593
   */
  private static boolean isCaretMovementUndoTransparent() {
    return Registry.is("ide.undo.transparent.caret.movement") ||
           AdvancedSettings.getBoolean("editor.undo.transparent.caret.movement");
  }
}
