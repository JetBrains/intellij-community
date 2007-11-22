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
import com.intellij.openapi.editor.SelectionModel;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMethodOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    // trim it if it's necessary
    GroovyRefactoringUtil.trimSpacesAndComments(editor, file, false);
    invokeOnEditor(project, editor, file);
  }

  private boolean invokeOnEditor(Project project, Editor editor, PsiFile file) {

    //todo implement in GSP files
    if (!(file instanceof GroovyFileBase /* || file instanceof GspFile*/)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      showErrorMessage(message, project);
      return false;
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    int startOffset = selectionModel.getSelectionStart();
    int endOffset = selectionModel.getSelectionEnd();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] elements = ExtractMethodUtil.getElementsInOffset(file, startOffset, endOffset);
    if (elements.length == 1 && elements[0] instanceof GrExpression) {
      selectionModel.setSelection(startOffset, elements[0].getTextRange().getEndOffset());
    }
    
    GrStatement[] statements = ExtractMethodUtil.getStatementsByElements(elements);

    if (statements.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.should.represent.a.statement.set"));
      showErrorMessage(message, project);
      return false;
    }

    // test for this or super constructor calls
    for (GrStatement statement : statements) {
      if (GroovyRefactoringUtil.isSuperOrThisCall(statement, true, true)) {
        String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
        showErrorMessage(message, project);
        return false;
      }
    }

    GrMethodOwner methodOwner = ExtractMethodUtil.getMethodOwner(statements[0]);
    GrVariableDeclarationOwner declarationOwner = ExtractMethodUtil.getDecalarationOwner(statements[0]);
    if (methodOwner == null ||
        (declarationOwner == null && !ExtractMethodUtil.isSingleExpression(statements))) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
      showErrorMessage(message, project);
      return false;
    }
    if (declarationOwner == null &&
        ExtractMethodUtil.isSingleExpression(statements) &&
        statements[0] instanceof GrExpression &&
        PsiType.VOID == ((GrExpression) statements[0]).getType()) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(message, project);
      return false;
    }


    // get information about variables in selected block
    VariableInfo variableInfo = ReachingDefinitionsCollector.obtainVariableFlowInformation(statements[0], statements[statements.length - 1]);
    String[] inputNames = variableInfo.getInputVariableNames();
    String[] outputNames = variableInfo.getOutputVariableNames();
    if (outputNames.length > 1) {
      String message = GroovyRefactoringBundle.message("multiple.output.values");
      showErrorMessage(message, project);
      return false;
    }

    // map names to types
    String outputName = outputNames.length == 0 ? null : outputNames[0];
    Map<String, PsiType> typeMap = ExtractMethodUtil.getVariableTypes(statements);
    if (typeMap == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("cannot.perform.analysis"));
      showErrorMessage(message, project);
      return false;
    }

    boolean canBeStatic = ExtractMethodUtil.canBeStatic(statements[0]);

    ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(inputNames, outputName, typeMap, elements, statements, methodOwner, canBeStatic);

    ExtractMethodSettings settings = getSettings(helper);
    if (!settings.isOK()) {
      return false;
    }

    final String methodName = settings.getEnteredName();
    helper = settings.getHelper();

    assert methodName != null;
    runRefactoring(methodName, helper, methodOwner, declarationOwner, editor);

    return true;
  }

  private void runRefactoring(final String methodName,
                              @NotNull final ExtractMethodInfoHelper helper,
                              @NotNull final GrMethodOwner methodOwner,
                              final GrVariableDeclarationOwner declarationOwner,
                              final Editor editor) {

    // todo remove me!
    final GrMethod method = ExtractMethodUtil.createMethodByHelper(methodName, helper);
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          GrMethod newMethod = methodOwner.addMethod(method);
          ExtractMethodUtil.renameParameterOccurrences(newMethod, helper);
          PsiUtil.shortenReferences(newMethod);
          GrStatement realStatement;

          if (declarationOwner != null && !ExtractMethodUtil.isSingleExpression(helper.getStatements())) {
            // Replace set of statements
            final GrStatement newStatement = ExtractMethodUtil.createResultStatement(helper, methodName);
            // add call statement
            final GrStatement[] statements = helper.getStatements();
            assert statements.length > 0;
            realStatement = declarationOwner.addStatementBefore(newStatement, statements[0]);
            // remove old statements
            ExtractMethodUtil.removeOldStatements(declarationOwner, helper);
            ExtractMethodUtil.removeNewLineAfter(realStatement);
          } else {
            // Expression call replace
            GrExpression methodCall = ExtractMethodUtil.createMethodCallByHelper(methodName, helper);
            GrExpression oldExpr = (GrExpression) helper.getStatements()[0];
            realStatement = oldExpr.replaceWithExpression(methodCall , true);
          }

          // move to offset
          if (editor != null) {
            PsiDocumentManager.getInstance(helper.getProject()).commitDocument(editor.getDocument());
            editor.getCaretModel().moveToOffset(ExtractMethodUtil.getCaretOffset(realStatement));
          }

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
            editor.getSelectionModel().removeSelection();
          }
        }, REFACTORING_NAME, null);


  }

  private ExtractMethodSettings getSettings(@NotNull ExtractMethodInfoHelper helper) {
    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(helper, helper.getProject());
    dialog.show();
    return dialog;
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }
}
