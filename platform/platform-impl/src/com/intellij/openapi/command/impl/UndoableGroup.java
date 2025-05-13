// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class UndoableGroup implements Dumpable {
  private static final Logger LOG = Logger.getInstance(UndoableGroup.class);
  private static final int BULK_MODE_ACTION_THRESHOLD = 50;

  private final @NlsContexts.Command String myCommandName;
  private final boolean myGlobal;
  private final int myCommandTimestamp;
  private final boolean myTransparent;
  private final List<? extends UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private UndoableGroupOriginalContext myGroupOriginalContext;
  private final Project myProject;
  private final UndoConfirmationPolicy myConfirmationPolicy;
  private boolean myTemporary;

  private boolean myValid;

  UndoableGroup(@NlsContexts.Command String commandName,
                boolean isGlobal,
                int commandTimestamp,
                EditorAndState stateBefore,
                EditorAndState stateAfter,
                @NotNull List<? extends UndoableAction> actions,
                @NotNull UndoRedoStacksHolder stacksHolder,
                @Nullable Project project,
                UndoConfirmationPolicy confirmationPolicy,
                boolean transparent,
                boolean valid) {
    myCommandName = commandName;
    myGlobal = isGlobal;
    myCommandTimestamp = commandTimestamp;
    myActions = actions;
    myProject = project;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myConfirmationPolicy = confirmationPolicy;
    myTransparent = transparent;
    myValid = valid;
    composeStartFinishGroup(stacksHolder);
    myTemporary = transparent;
  }

  boolean isGlobal() {
    return myGlobal;
  }

  boolean isTransparent() {
    return myTransparent;
  }

  /**
   * We allow transparent actions to be performed while we're in the middle of undo stack, without breaking it (i.e. without dropping
   * redo stack contents). Such actions are stored in undo stack as 'temporary' actions, and are dropped (not further kept in stacks)
   * on undo/redo. If a non-transparent action is performed after a temporary one, the latter is converted to normal (permanent) action,
   * and redo stack is cleared.
   */
  boolean isTemporary() {
    return myTemporary;
  }

  void makePermanent() {
    myTemporary = false;
  }

  boolean isUndoable() {
    for (UndoableAction action : myActions) {
      if (action instanceof NonUndoableAction) return false;
    }
    return true;
  }

  void setOriginalContext(@NotNull UndoableGroupOriginalContext originalContext){
    myGroupOriginalContext = originalContext;
  }

  @Nullable
  UndoableGroupOriginalContext getGroupOriginalContext(){
    return myGroupOriginalContext;
  }

  void undo() throws UnexpectedUndoException {
    undoOrRedo(true);
  }

  void redo() throws UnexpectedUndoException {
    undoOrRedo(false);
  }

  private void undoOrRedo(boolean isUndo) throws UnexpectedUndoException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Performing " + (isUndo ? "undo" : "redo") + " for " + dumpState());
    }
    LocalHistoryAction action;
    if (myProject != null && isGlobal()) {
      String actionName = IdeBundle.message(isUndo ? "undo.command" : "redo.command", myCommandName);
      action = LocalHistory.getInstance().startAction(actionName);
    }
    else {
      action = LocalHistoryAction.NULL;
    }

    try {
      doUndoOrRedo(isUndo);
    }
    finally {
      action.finish();
    }
  }

  private void doUndoOrRedo(final boolean isUndo) throws UnexpectedUndoException {
    // perform undo action by action, setting bulk update flag if possible
    // if multiple consecutive actions share a document, then set the bulk flag only once
    final UnexpectedUndoException[] exception = {null};
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        List<? extends UndoableAction> actionsList = isUndo ? ContainerUtil.reverse(myActions) : myActions;
        int toProcess = 0; // index of first action not yet performed
        int toProcessInBulk = 0; // index of first action that can be executed in bulk mode
        int actionCount = actionsList.size();
        for (int i = 0; i < actionCount; i++) {
          UndoableAction action = actionsList.get(i);
          DocumentEx newDocument = getDocumentToSetBulkMode(action);
          if (newDocument == null) {
            if (i - toProcessInBulk > BULK_MODE_ACTION_THRESHOLD) {
              performActions(actionsList.subList(toProcess, toProcessInBulk), isUndo, false);
              performActions(actionsList.subList(toProcessInBulk, i), isUndo, true);
              toProcess = i;
            }
            toProcessInBulk = i + 1;
          }
        }
        if (actionCount - toProcessInBulk > BULK_MODE_ACTION_THRESHOLD) {
          performActions(actionsList.subList(toProcess, toProcessInBulk), isUndo, false);
          performActions(actionsList.subList(toProcessInBulk, actionCount), isUndo, true);
        }
        else {
          performActions(actionsList.subList(toProcess, actionCount), isUndo, false);
        }
      }
      catch (UnexpectedUndoException e) {
        exception[0] = e;
      }
    });
    if (exception[0] != null) {
      throw exception[0];
    }
  }

  private static void performActions(@NotNull Collection<? extends UndoableAction> actions, boolean isUndo, boolean useBulkMode)
    throws UnexpectedUndoException {
    Set<DocumentEx> bulkDocuments = new HashSet<>();
    try {
      for (UndoableAction action : actions) {
        if (useBulkMode) {
          DocumentEx newDocument = getDocumentToSetBulkMode(action);
          if (newDocument == null) {
            for (DocumentEx document : bulkDocuments) {
              document.setInBulkUpdate(false);
            }
            bulkDocuments.clear();
          }
          else if (bulkDocuments.add(newDocument)) {
            newDocument.setInBulkUpdate(true);
          }
        }
        if (isUndo) {
          action.undo();
        }
        else {
          action.redo();
        }
      }
    }
    finally {
      for (DocumentEx bulkDocument : bulkDocuments) {
        bulkDocument.setInBulkUpdate(false);
      }
    }
  }

  @Override
  public @NotNull String dumpState() {
    return "UndoableGroup[project=" + myProject + ", name=" + myCommandName + ", global=" + myGlobal + ", transparent=" + myTransparent +
           ", stamp=" + myCommandTimestamp + ", policy=" + myConfirmationPolicy + ", temporary=" + myTemporary + ", valid=" + myValid +
           ", actions=" + myActions + ", documents=" + getAffectedDocuments() + "]";
  }

  private static DocumentEx getDocumentToSetBulkMode(UndoableAction action) {
    // We use bulk update only for EditorChangeAction, cause we know that it only changes document. Other actions can do things
    // not allowed in bulk update.
    if (!(action instanceof EditorChangeAction)) return null;
    //noinspection ConstantConditions
    DocumentReference newDocumentRef = action.getAffectedDocuments()[0];
    if (newDocumentRef == null) return null;
    VirtualFile file = newDocumentRef.getFile();
    if (file != null && !file.isValid()) return null;
    return  (DocumentEx)newDocumentRef.getDocument();
  }

  boolean isInsideStartFinishGroup(boolean isUndo, boolean isInsideStartFinishGroup) {
    final List<FinishMarkAction> finishMarks = new ArrayList<>();
    final List<StartMarkAction> startMarks = new ArrayList<>();
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) {
        startMarks.add((StartMarkAction)action);
      } else if (action instanceof FinishMarkAction) {
        finishMarks.add((FinishMarkAction)action);
      }
    }
    final int startNmb = startMarks.size();
    final int finishNmb = finishMarks.size();
    if (startNmb != finishNmb) {
      if (isUndo) {
        return finishNmb > startNmb;
      }
      else {
        return startNmb > finishNmb;
      }
    }
    return isInsideStartFinishGroup;
  }

  private void composeStartFinishGroup(final UndoRedoStacksHolder holder) {
    FinishMarkAction finishMark = getFinishMark();
    if (finishMark != null) {
      boolean global = false;
      String commandName = null;
      UndoRedoList<UndoableGroup> stack = holder.getStack(finishMark.getAffectedDocument());
      for (Iterator<UndoableGroup> iterator = stack.descendingIterator(); iterator.hasNext(); ) {
        UndoableGroup group = iterator.next();
        if (group.isGlobal()) {
          global = true;
          commandName = group.getCommandName();
          break;
        }
        if (group.getStartMark() != null) {
          break;
        }
      }
      if (global) {
        finishMark.setGlobal(true);
        finishMark.setCommandName(commandName);
      }
    }
  }

  private boolean shouldAskConfirmationForStartFinishGroup(boolean redo) {
    if (redo) {
      StartMarkAction mark = getStartMark();
      if (mark != null) {
        return mark.isGlobal();
      }
    }
    else {
      FinishMarkAction finishMark = getFinishMark();
      if (finishMark != null) {
        return finishMark.isGlobal();
      }
    }
    return false;
  }

  List<? extends UndoableAction> getActions() {
    return myActions;
  }

  @NotNull
  Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<>();
    for (UndoableAction action : myActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs != null) Collections.addAll(result, refs);
    }
    return result;
  }

  EditorAndState getStateBefore() {
    return myStateBefore;
  }

  EditorAndState getStateAfter() {
    return myStateAfter;
  }

  void setStateBefore(EditorAndState stateBefore) {
    myStateBefore = stateBefore;
  }

  void setStateAfter(EditorAndState stateAfter) {
    myStateAfter = stateAfter;
  }

  @NlsContexts.Command String getCommandName() {
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) {
        String commandName = ((StartMarkAction)action).getCommandName();
        if (commandName != null) return commandName;
      }
      else if (action instanceof FinishMarkAction) {
        String commandName = ((FinishMarkAction)action).getCommandName();
        if (commandName != null) return commandName;
      }
    }
    return myCommandName;
  }

  int getCommandTimestamp() {
    return myCommandTimestamp;
  }

  UndoConfirmationPolicy getConfirmationPolicy(){
    return myConfirmationPolicy;
  }

  private @Nullable StartMarkAction getStartMark() {
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) return (StartMarkAction)action;
    }
    return null;
  }

  private @Nullable FinishMarkAction getFinishMark() {
    for (UndoableAction action : myActions) {
      if (action instanceof FinishMarkAction) return (FinishMarkAction)action;
    }
    return null;
  }

  @ApiStatus.Experimental
  boolean shouldAskConfirmation(boolean redo) {
    if (shouldAskConfirmationForStartFinishGroup(redo)) return true;
    return myConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION ||
           myConfirmationPolicy != UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION && myGlobal;
  }

  long getGroupStartPerformedTimestamp() {
    if (myActions.isEmpty()) return -1L;
    return Math.min(myActions.get(0).getPerformedNanoTime(), myActions.get(myActions.size() - 1).getPerformedNanoTime());
  }

  void invalidateChangeRanges(SharedAdjustableUndoableActionsHolder adjustableUndoableActionsHolder) {
    for (UndoableAction action : myActions) {
      if (action instanceof AdjustableUndoableAction adjustableAction) {
        adjustableUndoableActionsHolder.remove(adjustableAction);
      }
    }
  }

  void invalidateActionsFor(DocumentReference ref) {
    if (getAffectedDocuments().contains(ref)) {
      myValid = false;
    }
  }

  boolean isValid() {
    return myValid;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("UndoableGroup[");
    final boolean multiline = myActions.size() > 1;

    if (multiline) result.append("\n");

    result.append(StringUtil.join(myActions, each -> (multiline ? "  " : "") + each.toString(), ",\n"));

    if (multiline) result.append("\n");
    result.append("]");
    return result.toString();
  }

  @NotNull String dumpState0() {
    return UndoUnit.fromGroup(this).toString();
  }

  boolean isSpeculativeUndoPossible() {
    if (!isGlobal() && isValid() && !isTransparent() && !isTemporary() && getConfirmationPolicy() == UndoConfirmationPolicy.DEFAULT) {
      if (UndoUtil.isSpeculativeUndoableCommand(getCommandName()) && !getActions().isEmpty()) {
        return ContainerUtil.and(
          getActions(),
          a -> a instanceof EditorChangeAction
        );
      }
    }
    return false;
  }

  static final class UndoableGroupOriginalContext {
    private final UndoableGroup myOriginalGroup;
    private final UndoableGroup myCurrentStackGroup;

    UndoableGroupOriginalContext(@NotNull UndoableGroup originalGroup, @NotNull UndoableGroup currentStackGroup) {
      myOriginalGroup = originalGroup;
      myCurrentStackGroup = currentStackGroup;
    }

    UndoableGroup getOriginalGroup() {
      return myOriginalGroup;
    }

    UndoableGroup getCurrentStackGroup(){
      return myCurrentStackGroup;
    }
  }
}
