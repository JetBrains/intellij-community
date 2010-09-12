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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
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
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyExtractMethodHandler implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.extractMethod.GroovyExtractMethodHandler");
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");
  private String myInvokeResult = "ok";

  protected void showErrorMessage(String message, final Project project, Editor editor) {
    Application application = ApplicationManager.getApplication();
    myInvokeResult = message;
    if (!application.isUnitTestMode()) {
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
    }
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    invokeOnEditor(project, editor, file);
  }

  boolean invokeOnEditor(Project project, Editor editor, PsiFile file) {
    // trim it if it's necessary
    GroovyRefactoringUtil.trimSpacesAndComments(editor, file, false);

    //todo implement in GSP files
    if (!(file instanceof GroovyFileBase /* || file instanceof GspFile*/)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      showErrorMessage(message, project, editor);
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
      showErrorMessage(message, project, editor);
      return false;
    }

    // test for this or super constructor calls
    for (GrStatement statement : statements) {
      if (GroovyRefactoringUtil.isSuperOrThisCall(statement, true, true)) {
        String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
        showErrorMessage(message, project, editor);
        return false;
      }
    }

    GrMemberOwner owner = ExtractMethodUtil.getMemberOwner(statements[0]);
    GrStatementOwner declarationOwner = ExtractMethodUtil.getDeclarationOwner(statements[0]);
    if (owner == null ||
        (declarationOwner == null && !ExtractMethodUtil.isSingleExpression(statements))) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
      showErrorMessage(message, project, editor);
      return false;
    }
    if (declarationOwner == null &&
        ExtractMethodUtil.isSingleExpression(statements) &&
        statements[0] instanceof GrExpression && PsiType.VOID.equals(((GrExpression)statements[0]).getType())) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(message, project, editor);
      return false;
    }

    // collect information about return statements in selected statement set
    ArrayList<GrStatement> returnStatements = new ArrayList<GrStatement>();
    for (GrStatement statement : statements) {
      returnStatements.addAll(ControlFlowUtils.collectReturns(statement, false));
    }

    // collect information about variables in selected block
    FragmentVariableInfos fragmentVariableInfos = ReachingDefinitionsCollector.obtainVariableFlowInformation(statements[0], statements[statements.length - 1]);
    VariableInfo[] inputInfos = fragmentVariableInfos.getInputVariableNames();
    VariableInfo[] outputInfos = fragmentVariableInfos.getOutputVariableNames();
    if (outputInfos.length > 1 ||
        outputInfos.length == 1 && returnStatements.size() > 0) {
      String message = GroovyRefactoringBundle.message("multiple.output.values");
      showErrorMessage(message, project, editor);
      return false;
    }

    boolean hasInterruptingStatements = false;

    for (GrStatement statement : statements) {
      if (hasInterruptingStatements =
          GroovyRefactoringUtil.hasWrongBreakStatements(statement) ||
              GroovyRefactoringUtil.haswrongContinueStatements(statement)) {
        break;
      }
    }
    // must be replaced by return statement
    boolean hasReturns = returnStatements.size() > 0;
    boolean isReturnStatement = ExtractMethodUtil.isReturnStatement(statements[statements.length - 1], returnStatements);
    if (!isReturnStatement && hasReturns || hasInterruptingStatements) {
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow");
      showErrorMessage(message, project, editor);
      return false;
    }

    VariableInfo outputInfo = outputInfos.length == 0 ? null : outputInfos[0];
    boolean canBeStatic = ExtractMethodUtil.canBeStatic(statements[0]);

    ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(inputInfos, outputInfo, elements, statements, owner, canBeStatic, isReturnStatement);

    final String methodName;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      GroovyExtractMethodDialog dialog = getDialog(helper);
      if (!dialog.isOK()) {
        return false;
      }
      ExtractMethodSettings settings = dialog.getSettings();
      methodName = settings.getEnteredName();
      helper = settings.getHelper();
    } else {
      methodName = "testMethod";
    }

    assert methodName != null;
    runRefactoring(methodName, helper, owner, declarationOwner, editor, statements[0]);

    return true;
  }

  private void runRefactoring(final String methodName,
                              @NotNull final ExtractMethodInfoHelper helper,
                              @NotNull final GrMemberOwner owner,
                              final GrStatementOwner declarationOwner,
                              final Editor editor,
                              final PsiElement startElement) {

    final GrMethod method = ExtractMethodUtil.createMethodByHelper(methodName, helper);
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiElement anchor = ExtractMethodUtil.calculateAnchorToInsertBefore(owner, startElement);
          GrMethod newMethod = owner.addMemberDeclaration(method, anchor);
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
            PsiImplUtil.removeNewLineAfter(realStatement);
          } else {
            // Expression call replace
            GrExpression methodCall = ExtractMethodUtil.createMethodCallByHelper(methodName, helper);
            GrExpression oldExpr = (GrExpression) helper.getStatements()[0];
            realStatement = oldExpr.replaceWithExpression(methodCall, true);
          }
          PsiUtil.shortenReferences(realStatement);

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

  private GroovyExtractMethodDialog getDialog(@NotNull final ExtractMethodInfoHelper helper) {
    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(helper, helper.getProject());
    dialog.show();
    return dialog;
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }

  public String getInvokeResult() {
    return myInvokeResult;
  }
}
