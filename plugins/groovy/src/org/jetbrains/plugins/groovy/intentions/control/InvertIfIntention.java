/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.IfEndInstruction;

/**
 * @author Niels Harremoes
 */
public class InvertIfIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    if (!"if".equals(element.getText()) || !(parent instanceof GrIfStatement)) {
      throw new IncorrectOperationException("Not invoked on an if");
    }
    GrIfStatement parentIf = (GrIfStatement)parent;
    GroovyPsiElementFactory groovyPsiElementFactory = GroovyPsiElementFactory.getInstance(project);


    GrExpression condition = parentIf.getCondition();
    if (condition == null) {
      throw new IncorrectOperationException("Invoked on an if with empty condition");
    }

    GrExpression negatedCondition = null;
    if (condition instanceof GrUnaryExpression) {
      GrUnaryExpression unaryCondition = (GrUnaryExpression)condition;
      if ("!".equals(unaryCondition.getOperationToken().getText())) {
        negatedCondition = stripParenthesis(unaryCondition.getOperand());
      }
    }

    if (negatedCondition == null) {
      // Now check whether this is a simple expression
      condition = stripParenthesis(condition);
      String negatedExpressionText;
      if (condition instanceof GrCallExpression || condition instanceof GrReferenceExpression) {
        negatedExpressionText = "!" + condition.getText();
      }
      else {
        negatedExpressionText = "!(" + condition.getText() + ")";
      }
      negatedCondition = groovyPsiElementFactory.createExpressionFromText(negatedExpressionText, parentIf);
    }


    GrStatement thenBranch = parentIf.getThenBranch();
    final boolean thenIsNotEmpty = isNotEmpty(thenBranch);

    String newIfText = "if (" + negatedCondition.getText() + ") {}";
    if (thenIsNotEmpty) {
      newIfText += " else {}";
    }

    GrIfStatement newIf = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(newIfText, parentIf.getContext());
    generateElseBranchTextAndRemoveTailStatements(parentIf, newIf);

    if (thenIsNotEmpty) {
      final GrStatement elseBranch = newIf.getElseBranch();
      assert elseBranch != null;
      elseBranch.replaceWithStatement(thenBranch);
    }

    parentIf.replace(newIf);
  }

  private static boolean isNotEmpty(@Nullable GrStatement thenBranch) {
    return thenBranch != null &&
           !(thenBranch instanceof GrBlockStatement && ((GrBlockStatement)thenBranch).getBlock().getStatements().length == 0);
  }

  private static void generateElseBranchTextAndRemoveTailStatements(@NotNull GrIfStatement ifStatement, @NotNull GrIfStatement newIf) {
    final GrStatement thenBranch = newIf.getThenBranch();
    assert thenBranch != null;

    GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      thenBranch.replaceWithStatement(elseBranch);
      return;
    }

    PsiElement parent = ifStatement.getParent();
    if (!(parent instanceof GrStatementOwner)) return;

    if (!isTailAfterIf(ifStatement, ((GrStatementOwner)parent))) return;

    final PsiElement start = ifStatement.getNextSibling();
    PsiElement end = parent instanceof GrCodeBlock ? ((GrCodeBlock)parent).getRBrace().getPrevSibling() : parent.getLastChild();

    final GrOpenBlock block = ((GrBlockStatement)thenBranch).getBlock();
    block.addRangeAfter(start, end, block.getLBrace());
    parent.deleteChildRange(start, end);
  }

  private static boolean isTailAfterIf(@NotNull GrIfStatement ifStatement, @NotNull GrStatementOwner owner) {
    final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(ifStatement);
    if (flowOwner == null) return false;

    final Instruction[] flow = flowOwner.getControlFlow();

    final GrStatement[] statements = owner.getStatements();
    final int index = ArrayUtilRt.find(statements, ifStatement);
    if (index == statements.length - 1) return false;

    final GrStatement then = ifStatement.getThenBranch();

    for (Instruction i : flow) {
      final PsiElement element = i.getElement();
      if (element == null || !PsiTreeUtil.isAncestor(then, element, true)) continue;

      for (Instruction succ : i.allSuccessors()) {
        if (succ instanceof IfEndInstruction) {
          return false;
        }
      }
    }

    return true;
  }

  @NotNull
  private static GrExpression stripParenthesis(GrExpression operand) {
    while (operand instanceof GrParenthesizedExpression) {
      GrExpression innerExpression = ((GrParenthesizedExpression)operand).getOperand();
      if (innerExpression == null) {
        break;
      }
      operand = innerExpression;
    }
    return operand;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement)) return false;
        if (((GrIfStatement)parent).getCondition() == null) return false;
        if (!"if".equals(element.getText())) return false;

        return true;
      }
    };
  }
}
