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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.inline.GroovyInlineMethodUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil.*;

/**
 * @author Max Medvedev
 */
public class GroovyExtractChooser {
  private static final Logger LOG = Logger.getInstance(GroovyExtractChooser.class);

  public static InitialInfo invoke(Project project, Editor editor, PsiFile file, int start, int end, boolean forceStatements) throws GrRefactoringError {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!(file instanceof GroovyFileBase)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
      throw new GrRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] elements = getElementsInOffset(file, start, end, forceStatements);
    if (elements.length == 1 && elements[0] instanceof GrExpression) {
      selectionModel.setSelection(start, elements[0].getTextRange().getEndOffset());
    }

    GrStatement[] statements = getStatementsByElements(elements);

    if (statements.length == 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.should.represent.a.statement.set"));
    }

    for (GrStatement statement : statements) {
      if (isSuperOrThisCall(statement, true, true)) {
        throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
      }
    }

    GrStatement statement0 = statements[0];
    GrMemberOwner owner = PsiUtil.getMemberOwner(statement0);
    GrStatementOwner declarationOwner = getDeclarationOwner(statement0);
    if (owner == null || declarationOwner == null && !ExtractUtil.isSingleExpression(statements)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }
    if (declarationOwner == null &&
        ExtractUtil.isSingleExpression(statements) &&
        statement0 instanceof GrExpression &&
        PsiType.VOID.equals(((GrExpression)statement0).getType())) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
    }

    if (ExtractUtil.isSingleExpression(statements) && GrIntroduceHandlerBase.expressionIsIncorrect((GrExpression)statement0)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.should.represent.an.expression"));
    }

    if (ExtractUtil.isSingleExpression(statements) &&
        statement0.getParent() instanceof GrAssignmentExpression &&
        ((GrAssignmentExpression)statement0.getParent()).getLValue() == statement0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.expression.should.not.be.lvalue"));
    }

    // collect information about return statements in selected statement set

    Set<GrStatement> allReturnStatements = new HashSet<GrStatement>();
    GrControlFlowOwner controlFlowOwner = ControlFlowUtils.findControlFlowOwner(statement0);
    LOG.assertTrue(controlFlowOwner != null);
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
      throw new GrRefactoringError(GroovyRefactoringBundle.message("multiple.output.values"));
    }

    boolean hasInterruptingStatements = false;

    for (GrStatement statement : statements) {
      hasInterruptingStatements = hasWrongBreakStatements(statement) || hasWrongContinueStatements(statement);
      if (hasInterruptingStatements) break;
    }

    // must be replaced by return statement
    boolean hasReturns = returnStatements.size() > 0;
    List<GrStatement> returnStatementsCopy = new ArrayList<GrStatement>(returnStatements.size());
    returnStatementsCopy.addAll(returnStatements);
    boolean isReturnStatement = isReturnStatement(statements[statements.length - 1], returnStatementsCopy);
    boolean isLastStatementOfMethod = isLastStatementOfMethodOrClosure(statements);
    if (hasReturns && !isLastStatementOfMethod && !isReturnStatement || hasInterruptingStatements) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow"));
    }

    return new InitialInfo(inputInfos, outputInfos, elements, statements, returnStatements);
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

  private static GrStatement[] getStatementsByElements(PsiElement[] elements) {
    ArrayList<GrStatement> statementList = new ArrayList<GrStatement>();
    for (PsiElement element : elements) {
      if (element instanceof GrStatement) {
        statementList.add(((GrStatement)element));
      }
    }
    return statementList.toArray(new GrStatement[statementList.size()]);
  }

  private static PsiElement[] getElementsInOffset(PsiFile file, int startOffset, int endOffset, boolean forceStatements) {
    GrExpression expr = findElementInRange(file, startOffset, endOffset, GrExpression.class);
    if (!forceStatements && expr != null) return new PsiElement[]{expr};

    if (expr == null) {
      return findStatementsInRange(file, startOffset, endOffset, true);
    }

    if (expr.getParent() instanceof GrMethodCallExpression) {
      expr = ((GrExpression)expr.getParent());
    }
    return new PsiElement[]{expr};
  }

  private static boolean isReturnStatement(GrStatement statement, Collection<GrStatement> returnStatements) {
    if (statement instanceof GrReturnStatement) return true;
    if (statement instanceof GrIfStatement) {
      boolean checked = GroovyInlineMethodUtil.checkTailIfStatement(((GrIfStatement)statement), returnStatements);
      return checked & returnStatements.size() == 0;
    }
    if (statement instanceof GrExpression) {
      return returnStatements.contains(statement);
    }
    return false;
  }
}
