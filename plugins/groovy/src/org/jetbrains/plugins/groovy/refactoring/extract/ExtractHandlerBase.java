/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public abstract class ExtractHandlerBase<InfoHelper extends ExtractInfoHelper> implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractHandlerBase.class);

  public void invokeOnEditor(Project project, Editor editor, PsiFile file, int start, int end) throws ExtractException {
    /*// trim it if it's necessary
    GroovyRefactoringUtil.trimSpacesAndComments(editor, file, false);*/

    if (!(file instanceof GroovyFileBase)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      throw new ExtractException(message);
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] elements = ExtractUtil.getElementsInOffset(file, start, end);
    if (elements.length == 1 && elements[0] instanceof GrExpression) {
      selectionModel.setSelection(start, elements[0].getTextRange().getEndOffset());
    }

    GrStatement[] statements = ExtractUtil.getStatementsByElements(elements);

    if (statements.length == 0) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.should.represent.a.statement.set"));
      throw new ExtractException(message);
    }

    for (GrStatement statement : statements) {
      if (GroovyRefactoringUtil.isSuperOrThisCall(statement, true, true)) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
        throw new ExtractException(message);
      }
    }

    GrStatement statement0 = statements[0];
    GrMemberOwner owner = ExtractUtil.getMemberOwner(statement0);
    GrStatementOwner declarationOwner = ExtractUtil.getDeclarationOwner(statement0);
    if (owner == null || declarationOwner == null && !ExtractUtil.isSingleExpression(statements)) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
      throw new ExtractException(message);
    }
    if (declarationOwner == null &&
        ExtractUtil.isSingleExpression(statements) &&
        statement0 instanceof GrExpression &&
        PsiType.VOID.equals(((GrExpression)statement0).getType())) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
      throw new ExtractException(message);
    }


    // collect information about return statements in selected statement set

    Set<GrStatement> allReturnStatements = new HashSet<GrStatement>();
    GrControlFlowOwner controlFlowOwner = ControlFlowUtils.findControlFlowOwner(statement0);
    assert controlFlowOwner != null;
    allReturnStatements.addAll(ControlFlowUtils.collectReturns(controlFlowOwner, true));

    ArrayList<GrStatement> returnStatements = new ArrayList<GrStatement>();
    for (GrStatement returnStatement : allReturnStatements) {
      for (GrStatement statement : statements) {
        if (PsiTreeUtil.isAncestor(statement, returnStatement, false)) {
          returnStatements.add(returnStatement);
          break;
        }
      }
    }

    // collect information about variables in selected block
    FragmentVariableInfos
      fragmentVariableInfos = ReachingDefinitionsCollector.obtainVariableFlowInformation(statement0, statements[statements.length - 1]);
    VariableInfo[] inputInfos = fragmentVariableInfos.getInputVariableNames();
    VariableInfo[] outputInfos = fragmentVariableInfos.getOutputVariableNames();
    if (outputInfos.length == 1 && returnStatements.size() > 0) {
      String message = GroovyRefactoringBundle.message("multiple.output.values");
      throw new ExtractException(message);
    }

    boolean hasInterruptingStatements = false;

    for (GrStatement statement : statements) {
      hasInterruptingStatements =
        GroovyRefactoringUtil.hasWrongBreakStatements(statement) || GroovyRefactoringUtil.haswrongContinueStatements(statement);
      if (hasInterruptingStatements) break;
    }

    // must be replaced by return statement
    boolean hasReturns = returnStatements.size() > 0;
    List<GrStatement> returnStatementsCopy = new ArrayList<GrStatement>(returnStatements.size());
    returnStatementsCopy.addAll(returnStatements);
    boolean isReturnStatement = ExtractUtil.isReturnStatement(statements[statements.length - 1], returnStatementsCopy);
    boolean isLastStatementOfMethod = isLastStatementOfMethodOrClosure(statements);
    if (hasReturns && !isLastStatementOfMethod && !isReturnStatement || hasInterruptingStatements) {
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow");
      throw new ExtractException(message);
    }

    InitialInfo info = new InitialInfo(inputInfos, outputInfos, elements, statements, owner, returnStatements);

    InfoHelper helper = getSettings(info);
    if (helper == null) return;

    performRefactoring(helper, owner, declarationOwner, editor, statement0);
  }

  private static boolean isLastStatementOfMethodOrClosure(GrStatement[] statements) {
    final GrStatement statement0 = statements[0];

    PsiElement returnFrom = PsiTreeUtil.getParentOfType(statement0, GrMethod.class, GrClosableBlock.class, GroovyFile.class);
    if (returnFrom instanceof GrMethod) {
      returnFrom = ((GrMethod)returnFrom).getBlock();
    }
    LOG.assertTrue(returnFrom instanceof GrStatementOwner);

    final GrStatement[] blockStatements = ((GrStatementOwner)returnFrom).getStatements();
    final GrStatement lastFromBlock = ArrayUtil.getLastElement(blockStatements);
    final GrStatement lastStatement = ArrayUtil.getLastElement(statements);
    return statement0.getManager().areElementsEquivalent(lastFromBlock, lastStatement);
  }

  @Nullable
  public abstract InfoHelper getSettings(@NotNull final InitialInfo initialInfo);

  public abstract void performRefactoring(@NotNull final InfoHelper helper,
                                          @NotNull final GrMemberOwner owner,
                                          final GrStatementOwner declarationOwner,
                                          final Editor editor,
                                          final PsiElement startElement);
}
