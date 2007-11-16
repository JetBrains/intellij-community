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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMethodOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author ilyas
 */
public class GroovyExtractMethodHandler implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.extractMethod.GroovyExtractMethodHandler");
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
    GroovyRefactoringUtil.trimSpacesAndComments(editor, file, false);
    if (invokeOnEditor(project, editor, file)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  private boolean invokeOnEditor(Project project, Editor editor, PsiFile file) {

    //todo implement in GSP files
    if (!(file instanceof GroovyFileBase /* || file instanceof GspFile*/)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      showErrorMessage(message, project);
      return false;
    }

    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] elements = ExtractMethodUtil.getElementsInOffset(file, startOffset, endOffset);
    GrStatement[] statements = ExtractMethodUtil.getStatementsByElements(elements);

    if (statements.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.should.represent.a.statement.set"));
      showErrorMessage(message, project);
      return false;
    }

    GrMethodOwner owner = ExtractMethodUtil.getMethodOwner(statements[0]);
    if (owner == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
      showErrorMessage(message, project);
      return false;
    }

    // get information about variables in selected block
    VariableInfo variableInfo = ReachingDefinitionsCollector.obtainVariableFlowInformation(statements[0], statements[statements.length - 1]);
    String[] inputNames = variableInfo.getInputVariableNames();
    String[] outputNames = variableInfo.getOutputVariableNames();
    if (outputNames.length > 1) {
      String message = RefactoringBundle.getCannotRefactorMessage("multiple.output.values");
      showErrorMessage(message, project);
      return false;
    }

    // map names to types
    String outputName = outputNames.length == 0 ? null : outputNames[0];
    Map<String,PsiType> typeMap = ExtractMethodUtil.getVariableTypes(statements);
    if (typeMap == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("cannot.perform.analysis"));
      showErrorMessage(message, project);
      return false;
    }

    ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(inputNames, outputName, typeMap, elements);

    // todo implement EM dialog logic

    runRefactoring(helper, owner);

    return true;
  }

  private void runRefactoring(@NotNull ExtractMethodInfoHelper helper, @NotNull final GrMethodOwner owner) {

    final GrMethod method = ExtractMethodUtil.createMethodByHelper("bliss", helper);

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          owner.addMethod(method);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    Project project = helper.getProject();

    CommandProcessor.getInstance().executeCommand(
        project,
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        }, REFACTORING_NAME, null);


  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }
}
