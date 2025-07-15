// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.AdjustableUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class UndoableGroup implements Dumpable {
  private static final Logger LOG = Logger.getInstance(UndoableGroup.class);
  private static final int BULK_MODE_ACTION_THRESHOLD = 50;

  private final @Nullable Project project;
  private final @Nullable @Command String commandName;
  private final @NotNull List<? extends UndoableAction> actions;
  private final @NotNull UndoConfirmationPolicy confirmationPolicy;
  private final @Nullable UndoCommandFlushReason flushReason;
  private final int commandTimestamp;
  private final boolean isTransparent;
  private final boolean isGlobal;
  private final boolean isUndoable;

  private @Nullable UndoableGroupOriginalContext originalContext;
  private @Nullable EditorAndState stateBefore;
  private @Nullable EditorAndState stateAfter;
  private boolean isTemporary;
  private boolean isValid;

  UndoableGroup(
    @Nullable Project project,
    @Nullable @Command String commandName,
    @NotNull List<? extends UndoableAction> actions,
    @NotNull UndoConfirmationPolicy confirmationPolicy,
    @NotNull UndoRedoStacksHolder stacksHolder,
    @Nullable EditorAndState stateBefore,
    @Nullable EditorAndState stateAfter,
    @Nullable UndoCommandFlushReason flushReason,
    int commandTimestamp,
    boolean isTransparent,
    boolean isGlobal,
    boolean isValid
  ) {
    this.project = project;
    this.commandName = commandName;
    this.actions = actions;
    this.confirmationPolicy = confirmationPolicy;
    this.originalContext = null;
    this.stateBefore = stateBefore;
    this.stateAfter = stateAfter;
    this.flushReason = flushReason;
    this.commandTimestamp = commandTimestamp;
    this.isTransparent = isTransparent;
    this.isTemporary = isTransparent;
    this.isGlobal = isGlobal;
    this.isValid = isValid;
    composeStartFinishGroup(stacksHolder);
    this.isUndoable = ContainerUtil.all(actions, action -> !(action instanceof NonUndoableAction));
  }

  boolean isUndoable() {
    return isUndoable;
  }

  void undo() throws UnexpectedUndoException {
    undoOrRedo(true);
  }

  void redo() throws UnexpectedUndoException {
    undoOrRedo(false);
  }

  boolean isInsideStartFinishGroup(boolean isUndo, boolean isInsideStartFinishGroup) {
    int startNmb = 0;
    int finishNmb = 0;
    for (UndoableAction action : actions) {
      if (action instanceof StartMarkAction) {
        startNmb++;
      }
      else if (action instanceof FinishMarkAction) {
        finishNmb++;
      }
    }
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

  boolean shouldAskConfirmation(boolean redo) {
    if (shouldAskConfirmationForStartFinishGroup(redo)) {
      return true;
    }
    return confirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION ||
           confirmationPolicy != UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION && isGlobal;
  }

  @Command String getCommandName() {
    for (UndoableAction action : actions) {
      if (action instanceof StartMarkAction startMark) {
        String commandName = startMark.getCommandName();
        if (commandName != null) {
          return commandName;
        }
      } else if (action instanceof FinishMarkAction finishMark) {
        String commandName = finishMark.getCommandName();
        if (commandName != null) {
          return commandName;
        }
      }
    }
    return commandName;
  }

  @NotNull UndoConfirmationPolicy getConfirmationPolicy() {
    return confirmationPolicy;
  }

  @NotNull List<? extends UndoableAction> getActions() {
    return actions;
  }

  boolean isGlobal() {
    return isGlobal;
  }

  boolean isTransparent() {
    return isTransparent;
  }

  int getCommandTimestamp() {
    return commandTimestamp;
  }

  /**
   * We allow transparent actions to be performed while we're in the middle of undo stack, without breaking it (i.e. without dropping
   * redo stack contents). Such actions are stored in undo stack as 'temporary' actions, and are dropped (not further kept in stacks)
   * on undo/redo. If a non-transparent action is performed after a temporary one, the latter is converted to normal (permanent) action,
   * and redo stack is cleared.
   */
  boolean isTemporary() {
    return isTemporary;
  }

  void makePermanent() {
    isTemporary = false;
  }

  boolean isValid() {
    return isValid;
  }

  long getGroupStartPerformedTimestamp() {
    if (actions.isEmpty()) {
      return -1L;
    }
    return Math.min(
      actions.get(0).getPerformedNanoTime(),
      actions.get(actions.size() - 1).getPerformedNanoTime()
    );
  }

  void invalidateChangeRanges(@NotNull SharedAdjustableUndoableActionsHolder adjustableUndoableActionsHolder) {
    for (UndoableAction action : actions) {
      if (action instanceof AdjustableUndoableAction adjustableAction) {
        adjustableUndoableActionsHolder.remove(adjustableAction);
      }
    }
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (getAffectedDocuments().contains(ref)) {
      isValid = false;
    }
  }

  @NotNull Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<>();
    for (UndoableAction action : actions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs != null) {
        Collections.addAll(result, refs);
      }
    }
    return result;
  }

  void setOriginalContext(@NotNull UndoableGroupOriginalContext originalContext) {
    this.originalContext = originalContext;
  }

  @Nullable UndoableGroupOriginalContext getOriginalContext() {
    return originalContext;
  }

  void setStateBefore(@NotNull EditorAndState stateBefore) {
    this.stateBefore = stateBefore;
  }

  void setStateAfter(@NotNull EditorAndState stateAfter) {
    this.stateAfter = stateAfter;
  }

  @Nullable EditorAndState getStateBefore() {
    return stateBefore;
  }

  @Nullable EditorAndState getStateAfter() {
    return stateAfter;
  }

  @Nullable UndoCommandFlushReason getFlushReason() {
    return flushReason;
  }

  private void undoOrRedo(boolean isUndo) throws UnexpectedUndoException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Performing " + (isUndo ? "undo" : "redo") + " for " + dumpState());
    }
    LocalHistoryAction action;
    if (project != null && isGlobal()) {
      String actionName = IdeBundle.message(isUndo ? "undo.command" : "redo.command", commandName);
      action = LocalHistory.getInstance().startAction(actionName);
    } else {
      action = LocalHistoryAction.NULL;
    }
    try {
      doUndoOrRedo(isUndo);
    } finally {
      action.finish();
    }
  }

  private void doUndoOrRedo(boolean isUndo) throws UnexpectedUndoException {
    // perform undo action by action, setting bulk update flag if possible
    // if multiple consecutive actions share a document, then set the bulk flag only once
    UnexpectedUndoException[] exception = {null};
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        List<? extends UndoableAction> actionsList = isUndo ? ContainerUtil.reverse(actions) : actions;
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

  private void composeStartFinishGroup(@NotNull UndoRedoStacksHolder holder) {
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

  private @Nullable StartMarkAction getStartMark() {
    for (UndoableAction action : actions) {
      if (action instanceof StartMarkAction startMark) {
        return startMark;
      }
    }
    return null;
  }

  private @Nullable FinishMarkAction getFinishMark() {
    for (UndoableAction action : actions) {
      if (action instanceof FinishMarkAction finishMark) {
        return finishMark;
      }
    }
    return null;
  }

  private static void performActions(
    @NotNull List<? extends UndoableAction> actions,
    boolean isUndo,
    boolean useBulkMode
  ) throws UnexpectedUndoException {
    Set<DocumentEx> bulkDocuments = new HashSet<>();
    try {
      for (UndoableAction action : actions) {
        if (useBulkMode) {
          DocumentEx newDocument = getDocumentToSetBulkMode(action);
          if (newDocument == null) {
            for (DocumentEx document : bulkDocuments) {
              //noinspection deprecation
              document.setInBulkUpdate(false);
            }
            bulkDocuments.clear();
          } else if (bulkDocuments.add(newDocument)) {
            //noinspection deprecation
            newDocument.setInBulkUpdate(true);
          }
        }
        if (isUndo) {
          action.undo();
        } else {
          action.redo();
        }
      }
    } finally {
      for (DocumentEx bulkDocument : bulkDocuments) {
        //noinspection deprecation
        bulkDocument.setInBulkUpdate(false);
      }
    }
  }

  private static @Nullable DocumentEx getDocumentToSetBulkMode(@NotNull UndoableAction action) {
    // We use bulk update only for EditorChangeAction, cause we know that it only changes document.
    // Other actions can do things not allowed in bulk update.
    if (!(action instanceof EditorChangeAction)) {
      return null;
    }
    //noinspection ConstantConditions
    DocumentReference newDocumentRef = action.getAffectedDocuments()[0];
    if (newDocumentRef == null) {
      return null;
    }
    VirtualFile file = newDocumentRef.getFile();
    if (file != null && !file.isValid()) {
      return null;
    }
    return (DocumentEx)newDocumentRef.getDocument();
  }

  @NotNull String dumpState0() {
    return UndoDumpUnit.fromGroup(this).toString();
  }

  @Override
  public @NotNull String dumpState() {
    return "UndoableGroup[project=%s, name=%s, global=%s, transparent=%s, stamp=%s, policy=%s, temporary=%s, valid=%s, actions=%s, documents=%s]"
      .formatted(project, commandName, isGlobal, isTransparent, commandTimestamp, confirmationPolicy, isTemporary, isValid, actions, getAffectedDocuments());
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("UndoableGroup[");
    boolean multiline = actions.size() > 1;
    if (multiline) {
      result.append("\n");
    }
    result.append(StringUtil.join(actions, each -> (multiline ? "  " : "") + each.toString(), ",\n"));
    if (multiline) {
      result.append("\n");
    }
    result.append("]");
    return result.toString();
  }
}
