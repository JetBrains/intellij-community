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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
class UndoableGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoableGroup");

  private final String myCommandName;
  private final boolean myGlobal;
  private final int myCommandTimestamp;
  private final boolean myTransparentsOnly;
  private final List<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private final Project myProject;
  private final UndoConfirmationPolicy myConfirmationPolicy;

  public UndoableGroup(String commandName,
                       boolean isGlobal,
                       Project project,
                       EditorAndState stateBefore,
                       EditorAndState stateAfter,
                       List<UndoableAction> actions,
                       int commandTimestamp,
                       UndoConfirmationPolicy confirmationPolicy,
                       boolean transparentsOnly) {
    myCommandName = commandName;
    myGlobal = isGlobal;
    myCommandTimestamp = commandTimestamp;
    myActions = actions;
    myProject = project;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myConfirmationPolicy = confirmationPolicy;
    myTransparentsOnly = transparentsOnly;
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public boolean isTransparentsOnly() {
    return myTransparentsOnly;
  }

  public boolean isUndoable() {
    for (UndoableAction action : myActions) {
      if (action instanceof NonUndoableAction) return false;
    }
    return true;
  }

  public void addTailActions(Collection<UndoableAction> actions) {
    myActions.addAll(actions);
  }

  public void undo() {
    undoOrRedo(true);
  }

  public void redo() {
    undoOrRedo(false);
  }

  private void undoOrRedo(boolean isUndo) {
    LocalHistoryAction action = LocalHistoryAction.NULL;

    if (myProject != null) {
      if (isGlobal()) {
        final String actionName;
        if (isUndo) {
          actionName = CommonBundle.message("local.vcs.action.name.undo.command", myCommandName);
        }
        else {
          actionName = CommonBundle.message("local.vcs.action.name.redo.command", myCommandName);
        }
        action = LocalHistory.getInstance().startAction(actionName);
      }
    }

    try {
      doUndoOrRedo(isUndo);
    }
    finally {
      action.finish();
    }
  }

  private void doUndoOrRedo(final boolean isUndo) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
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
    });
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
    if (myConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) return true;
    if (myConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) return false;
    return myGlobal;
  }

  public String toString() {
    @NonNls StringBuilder result = new StringBuilder("UndoableGroup{ ");
    for (UndoableAction action : myActions) {
      result.append(action).append(" ");
    }
    result.append("}");
    return result.toString();
  }
}
