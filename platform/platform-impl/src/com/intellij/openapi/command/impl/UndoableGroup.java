/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class UndoableGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoableGroup");

  private final String myCommandName;
  private final boolean myGlobal;
  private final int myCommandTimestamp;
  private final boolean myTransparent;
  private final List<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private final Project myProject;
  private final UndoConfirmationPolicy myConfirmationPolicy;

  private boolean myValid;

  public UndoableGroup(String commandName,
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
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public boolean isTransparent() {
    return myTransparent;
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
    LocalHistoryAction action;
    if (myProject != null && isGlobal()) {
      String actionName = CommonBundle.message(isUndo ? "local.vcs.action.name.undo.command" : "local.vcs.action.name.redo.command", myCommandName);
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
    final boolean wrapInBulkUpdate = myActions.size() > 50;
    // perform undo action by action, setting bulk update flag if possible
    // if multiple consecutive actions share a document, then set the bulk flag only once
    final Set<DocumentEx> bulkDocuments = new THashSet<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        for (final UndoableAction action : isUndo ? ContainerUtil.iterateBackward(myActions) : myActions) {
          if (wrapInBulkUpdate) {
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
      catch (UnexpectedUndoException e) {
        reportUndoProblem(e, isUndo);
      }
      finally {
        for (DocumentEx bulkDocument : bulkDocuments) {
          bulkDocument.setInBulkUpdate(false);
        }
      }
    });
    commitAllDocuments();
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

  private static void commitAllDocuments() {
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      PsiDocumentManager.getInstance(p).commitAllDocuments();
    }
  }

  private void reportUndoProblem(UnexpectedUndoException e, boolean isUndo) {
    String title;
    String message;

    if (isUndo) {
      title = CommonBundle.message("cannot.undo.dialog.title");
      message = CommonBundle.message("cannot.undo.message");
    }
    else {
      title = CommonBundle.message("cannot.redo.dialog.title");
      message = CommonBundle.message("cannot.redo.message");
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
