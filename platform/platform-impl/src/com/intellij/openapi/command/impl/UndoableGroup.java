/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

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
                       Project project,
                       EditorAndState stateBefore,
                       EditorAndState stateAfter,
                       List<UndoableAction> actions,
                       int commandTimestamp,
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

  private static void doInBulkMode(@NotNull final Runnable action, @NotNull Collection<DocumentEx> documents) {
    Runnable runnable = action;
    for (final DocumentEx document : documents) {
      final Runnable oldRunnable = runnable;
      runnable = new Runnable() {
        @Override
        public void run() {
          doInBulkMode(oldRunnable, document);
        }
      };
    }
    runnable.run();
  }
  private static void doInBulkMode(@NotNull Runnable action, @NotNull DocumentEx document) {
    boolean wasInBulkUpdate = document.isInBulkUpdate();
    document.setInBulkUpdate(true);
    try {
      action.run();
    }
    finally {
      if (!wasInBulkUpdate) {
        document.setInBulkUpdate(false);
      }
    }
  }


  private void doUndoOrRedo(final boolean isUndo) {
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          for (UndoableAction each : isUndo ? ContainerUtil.iterateBackward(myActions) : myActions) {
            if (isUndo) {
              each.undo();
            }
            else {
              each.redo();
            }
          }
        }
        catch (UnexpectedUndoException e) {
          reportUndoProblem(e, isUndo);
        }
      }
    };
    if (myActions.size() > 50) {
      final Collection<DocumentEx> documents = new THashSet<DocumentEx>();
      for (UndoableAction action : myActions) {
        DocumentReference[] affectedDocuments = action.getAffectedDocuments();
        if (affectedDocuments != null) {
          for (DocumentReference affectedDocument : affectedDocuments) {
            documents.add((DocumentEx)affectedDocument.getDocument());
          }
        }
      }
      final Runnable oldRunnable = runnable;
      runnable = new Runnable() {
        @Override
        public void run() {
          doInBulkMode(oldRunnable, documents);
        }
      };
    }

    ApplicationManager.getApplication().runWriteAction(runnable);
    commitAllDocuments();
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

  public Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new THashSet<DocumentReference>();
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
    return myCommandName;
  }

  public int getCommandTimestamp() {
    return myCommandTimestamp;
  }

  public boolean shouldAskConfirmation() {
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

    result.append(StringUtil.join(myActions, new Function<UndoableAction, String>() {
      @Override
      public String fun(UndoableAction each) {
        return (multiline ? "  " : "") + each.toString();
      }
    }, ",\n"));

    if (multiline) result.append("\n");
    result.append("]");
    return result.toString();
  }
}
