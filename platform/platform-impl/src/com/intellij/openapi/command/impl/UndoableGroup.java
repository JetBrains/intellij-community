// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class UndoableGroup implements Dumpable {
  private static final Logger LOG = Logger.getInstance(UndoableGroup.class);
  private static final int BULK_MODE_ACTION_THRESHOLD = 50;

  private final String myCommandName;
  private final boolean myGlobal;
  private final int myCommandTimestamp;
  private final boolean myTransparent;
  private final List<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private final Project myProject;
  private final UndoConfirmationPolicy myConfirmationPolicy;
  private boolean myTemporary;

  private boolean myValid;

  UndoableGroup(String commandName,
                       boolean isGlobal,
                       UndoManagerImpl manager,
                       EditorAndState stateBefore,
                       EditorAndState stateAfter,
                       List<UndoableAction> actions,
                       UndoConfirmationPolicy confirmationPolicy,
                       boolean transparent,
                       boolean valid) {
    myCommandName = commandName;
    myGlobal = isGlobal;
    myCommandTimestamp = manager.nextCommandTimestamp();
    myActions = actions;
    myProject = manager.getProject();
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myConfirmationPolicy = confirmationPolicy;
    myTransparent = transparent;
    myValid = valid;
    composeStartFinishGroup(manager.getUndoStacksHolder());
    myTemporary = transparent;
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public boolean isTransparent() {
    return myTransparent;
  }

  /**
   * We allow transparent actions to be performed while we're in the middle of undo stack, without breaking it (i.e. without dropping
   * redo stack contents). Such actions are stored in undo stack as 'temporary' actions, and are dropped (not further kept in stacks)
   * on undo/redo. If a non-transparent action is performed after a temporary one, the latter is converted to normal (permanent) action,
   * and redo stack is cleared.
   */
  public boolean isTemporary() {
    return myTemporary;
  }

  public void makePermanent() {
    myTemporary = false;
  }

  public boolean isUndoable() {
    for (UndoableAction action : myActions) {
      if (action instanceof NonUndoableAction) return false;
    }
    return true;
  }

  public void undo() {
    undoOrRedo(true);
  }

  public void redo() {
    undoOrRedo(false);
  }

  private void undoOrRedo(boolean isUndo) {
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

  private void doUndoOrRedo(final boolean isUndo) {
    // perform undo action by action, setting bulk update flag if possible
    // if multiple consecutive actions share a document, then set the bulk flag only once
    final UnexpectedUndoException[] exception = {null};
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        List<UndoableAction> actionsList = isUndo ? ContainerUtil.reverse(myActions) : myActions;
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
    if (exception[0] != null) reportUndoProblem(exception[0], isUndo);
  }

  private static void performActions(@NotNull Collection<? extends UndoableAction> actions, boolean isUndo, boolean useBulkMode)
    throws UnexpectedUndoException {
    Set<DocumentEx> bulkDocuments = new THashSet<>();
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

  @NotNull
  @Override
  public String dumpState() {
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
        if (finishNmb > startNmb) {
          return true;
        }
        else {
          return false;
        }
      }
      else {
        if (startNmb > finishNmb) {
          return true;
        }
        else {
          return false;
        }
      }
    }
    return isInsideStartFinishGroup;
  }

  void composeStartFinishGroup(final UndoRedoStacksHolder holder) {
    FinishMarkAction finishMark = getFinishMark();
    if (finishMark != null) {
      boolean global = false;
      String commandName = null;
      LinkedList<UndoableGroup> stack = holder.getStack(finishMark.getAffectedDocument());
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
        finishMark.setGlobal(global);
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

  private void reportUndoProblem(UnexpectedUndoException e, boolean isUndo) {
    String title;
    String message;

    if (isUndo) {
      title = IdeBundle.message("cannot.undo.title");
      message = IdeBundle.message("cannot.undo.message");
    }
    else {
      title = IdeBundle.message("cannot.redo.title");
      message = IdeBundle.message("cannot.redo.message");
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (e.getMessage() != null) {
        message += ".\n" + e.getMessage();
      }
      Messages.showMessageDialog(myProject, message, title, Messages.getErrorIcon());
    }
    else {
      LOG.error(e);
    }
  }

  public List<UndoableAction> getActions() {
    return myActions;
  }

  @NotNull
  public Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new THashSet<>();
    for (UndoableAction action : myActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs != null) Collections.addAll(result, refs);
    }
    return result;
  }

  public EditorAndState getStateBefore() {
    return myStateBefore;
  }

  public EditorAndState getStateAfter() {
    return myStateAfter;
  }

  public void setStateBefore(EditorAndState stateBefore) {
    myStateBefore = stateBefore;
  }

  public void setStateAfter(EditorAndState stateAfter) {
    myStateAfter = stateAfter;
  }

  public String getCommandName() {
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) {
        String commandName = ((StartMarkAction)action).getCommandName();
        if (commandName != null) return commandName;
      } else if (action instanceof FinishMarkAction) {
        String commandName = ((FinishMarkAction)action).getCommandName();
        if (commandName != null) return commandName;
      }
    }
    return myCommandName;
  }

  public int getCommandTimestamp() {
    return myCommandTimestamp;
  }

  @Nullable
  public StartMarkAction getStartMark() {
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) return (StartMarkAction)action;
    }
    return null;
  }

  @Nullable
  public FinishMarkAction getFinishMark() {
    for (UndoableAction action : myActions) {
      if (action instanceof FinishMarkAction) return (FinishMarkAction)action;
    }
    return null;
  }

  public boolean shouldAskConfirmation(boolean redo) {
    if (shouldAskConfirmationForStartFinishGroup(redo)) return true;
    return myConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION ||
           myConfirmationPolicy != UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION && myGlobal;
  }

  public void invalidateActionsFor(DocumentReference ref) {
    if (getAffectedDocuments().contains(ref)) {
      myValid = false;
    }
  }

  public boolean isValid() {
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
}
