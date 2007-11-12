/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyExtarctMethodHandler implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.extractMethod.GroovyExtarctMethodHandler");
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");

  protected void showErrorMessage(String message, final Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_METHOD, project);
  }

  protected boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    // trim it if it's necessary
    GroovyRefactoringUtil.trimSpaces(editor, file);
    if (invokeOnEditor(project, editor, file)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  private boolean invokeOnEditor(Project project, Editor editor, PsiFile file) {

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    //todo implement in GSP files
    if (!(file instanceof GroovyFileBase /* || file instanceof GspFile*/)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      showErrorMessage(message, project);
      return false;
    }

    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] statements = PsiElement.EMPTY_ARRAY;
    GrExpression expr = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) file), startOffset, endOffset, GrExpression.class);

    return false;
/*
    if (expr != null) {
      statements = new PsiElement[]{expr};
    } else {
      statements = GroovyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset);
    }
*/

    // find statements in selected range


  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }
}
