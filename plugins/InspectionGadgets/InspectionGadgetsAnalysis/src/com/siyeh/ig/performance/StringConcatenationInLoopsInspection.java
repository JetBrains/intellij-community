/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StringConcatenationInLoopsInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreUnlessAssigned = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("string.concatenation.in.loops.only.option"),
                                          this, "m_ignoreUnlessAssigned");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInLoopsVisitor();
  }

  private class StringConcatenationInLoopsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length <= 1) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) return;

      if (!checkExpression(expression, expression.getType())) return;

      if (ExpressionUtils.isEvaluatedAtCompileTime(expression)) return;

      if (m_ignoreUnlessAssigned && !isAppendedRepeatedly(expression)) return;
      final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
      assert sign != null;
      registerError(sign);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) return;

      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();

      if (!tokenType.equals(JavaTokenType.PLUSEQ)) return;

      PsiExpression lhs = expression.getLExpression();

      if (!checkExpression(expression, lhs.getType())) return;

      if (m_ignoreUnlessAssigned) {
        lhs = PsiUtil.skipParenthesizedExprDown(lhs);
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
      }
      registerError(sign);
    }

    private boolean checkExpression(PsiExpression expression, PsiType type) {
      if (!TypeUtils.isJavaLangString(type) || ControlFlowUtils.isInExitStatement(expression) ||
          !ControlFlowUtils.isInLoop(expression)) return false;

      PsiElement parent = expression;
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (parent != expression && parent instanceof PsiAssignmentExpression &&
          ((PsiAssignmentExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // Will be reported for parent +=, no need to report twice
        return false;
      }

      if (parent instanceof PsiAssignmentExpression) {
        expression = (PsiExpression)parent;
        PsiVariable variable = getAppendedVariable(expression);

        if (variable != null) {
          PsiLoopStatement commonLoop = getOutermostCommonLoop(expression, variable);
          return commonLoop != null && !flowBreaksLoop(PsiTreeUtil.getParentOfType(expression, PsiStatement.class), commonLoop);
        }
      }
      return !containingStatementExits(expression);
    }

    @Contract("null, _ -> false")
    private boolean flowBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
      if(statement == null || statement == loop) return false;
      for(PsiStatement sibling = statement; sibling != null; sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class)) {
        if(sibling instanceof PsiContinueStatement) return false;
        if(sibling instanceof PsiThrowStatement || sibling instanceof PsiReturnStatement) return true;
        if(sibling instanceof PsiBreakStatement) {
          PsiBreakStatement breakStatement = (PsiBreakStatement)sibling;
          PsiStatement exitedStatement = breakStatement.findExitedStatement();
          if(exitedStatement == loop) return true;
          return flowBreaksLoop(exitedStatement, loop);
        }
      }
      PsiElement parent = statement.getParent();
      if(parent == loop) return false;
      if(parent instanceof PsiCodeBlock) {
        PsiElement gParent = parent.getParent();
        if(gParent instanceof PsiBlockStatement || gParent instanceof PsiSwitchStatement) {
          return flowBreaksLoop((PsiStatement)gParent, loop);
        }
        return false;
      }
      if(parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement || parent instanceof PsiSwitchLabelStatement
        || parent instanceof PsiSwitchStatement) {
        return flowBreaksLoop((PsiStatement)parent, loop);
      }
      return false;
    }

    private PsiLoopStatement getOutermostCommonLoop(PsiExpression expression, PsiVariable variable) {
      PsiElement stopAt = null;
      PsiCodeBlock block = getSurroundingBlock(expression);
      if(block != null) {
        PsiElement ref;
        if(expression instanceof PsiAssignmentExpression) {
          ref = expression;
        } else {
          PsiReference reference = ReferencesSearch.search(variable, new LocalSearchScope(expression)).findFirst();
          ref = reference != null ? reference.getElement() : null;
        }
        if(ref != null) {
          PsiElement[] elements = StreamEx.of(DefUseUtil.getDefs(block, variable, expression)).prepend(expression).toArray(PsiElement[]::new);
          stopAt = PsiTreeUtil.findCommonParent(elements);
        }
      }
      PsiElement parent = expression.getParent();
      PsiLoopStatement commonLoop = null;
      while(parent != null && parent != stopAt && !(parent instanceof PsiMethod)
            && !(parent instanceof PsiClass) && !(parent instanceof PsiLambdaExpression)) {
        if(parent instanceof PsiLoopStatement) {
          commonLoop = (PsiLoopStatement)parent;
        }
        parent = parent.getParent();
      }
      return commonLoop;
    }

    @Nullable
    private PsiCodeBlock getSurroundingBlock(PsiElement expression) {
      PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiClassInitializer.class, PsiLambdaExpression.class);
      if(parent instanceof PsiMethod) {
        return ((PsiMethod)parent).getBody();
      } else if(parent instanceof PsiClassInitializer) {
        return ((PsiClassInitializer)parent).getBody();
      } else if(parent instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)parent).getBody();
        if(body instanceof PsiCodeBlock) {
          return (PsiCodeBlock)body;
        }
      }
      return null;
    }

    private boolean containingStatementExits(PsiElement element) {
      final PsiStatement newExpressionStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (newExpressionStatement == null) {
        return false;
      }
      final PsiStatement parentStatement = PsiTreeUtil.getParentOfType(newExpressionStatement, PsiStatement.class);
      return !ControlFlowUtils.statementMayCompleteNormally(parentStatement);
    }

    @Contract("null -> null")
    @Nullable
    private PsiVariable getAppendedVariable(PsiExpression expression) {
      if (!(expression instanceof PsiAssignmentExpression)) {
        return null;
      }
      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)expression).getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiElement element = ((PsiReferenceExpression)lhs).resolve();
      return element instanceof PsiVariable ? (PsiVariable)element : null;
    }

    private boolean isAppendedRepeatedly(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      if (assignmentExpression.getOperationTokenType() == JavaTokenType.PLUSEQ) {
        return true;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      return isAppended(variable, rhs);
    }

    private boolean isAppended(PsiVariable variable, PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if(expression instanceof PsiPolyadicExpression) {
        for(PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
          if(ExpressionUtils.isReferenceTo(operand, variable) || isAppended(variable, operand)) return true;
        }
      }
      return false;
    }
  }
}
