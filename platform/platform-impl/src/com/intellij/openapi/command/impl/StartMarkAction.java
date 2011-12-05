/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

/**
* User: anna
* Date: 11/8/11
*/
public class StartMarkAction extends BasicUndoableAction {
  private static StartMarkAction ourCurrentMark;
  private String myCommandName;
  private boolean myGlobal;
  private SmartPsiElementPointer<PsiNamedElement> myElementInfo;

  private StartMarkAction(Editor editor, PsiNamedElement element, String commandName) {
    super(DocumentReferenceManager.getInstance().create(editor.getDocument()));
    myCommandName = commandName;
    myElementInfo = element != null ? SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element) : null;
  }

  public void undo() {
  }

  public void redo() {
  }

  public void setGlobal(boolean global) {
    myGlobal = global;
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public void setCommandName(String commandName) {
    myCommandName = commandName;
  }

  public static StartMarkAction start(Editor editor, Project project, String commandName) throws AlreadyStartedException {
    return start(editor, project, null, commandName);
  }

  public static StartMarkAction start(Editor editor, Project project, PsiNamedElement namedElement, String commandName) throws AlreadyStartedException {
    if (ourCurrentMark != null) {
      throw new AlreadyStartedException(project, ourCurrentMark.myCommandName,
                                        ourCurrentMark.myElementInfo != null ? ourCurrentMark.myElementInfo.getElement() : null,
                                        ourCurrentMark.getAffectedDocuments());
    }
    final StartMarkAction markAction = new StartMarkAction(editor, namedElement, commandName);
    UndoManager.getInstance(project).undoableActionPerformed(markAction);
    ourCurrentMark = markAction;
    return markAction;
  }

  static void markFinished() {
    if (ourCurrentMark != null) {
      ourCurrentMark.myElementInfo = null;
    }
    ourCurrentMark = null;
  }

  public static class AlreadyStartedException extends Exception {
    private final DocumentReference[] myAffectedDocuments;
    private SmartPsiElementPointer<PsiNamedElement> myElementInfo;

    public AlreadyStartedException(Project project,
                                   String commandName,
                                   PsiNamedElement namedElement,
                                   DocumentReference[] document) {
      super("Unable to start inplace refactoring. " + commandName + " already started");
      myAffectedDocuments = document;
      if (namedElement != null) {
        myElementInfo = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(namedElement);
      } else {
        myElementInfo = null;
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      return myAffectedDocuments;
    }

    @Nullable
    public PsiNamedElement getElement() {
      return myElementInfo != null ? myElementInfo.getElement() : null;
    }
  }
}
