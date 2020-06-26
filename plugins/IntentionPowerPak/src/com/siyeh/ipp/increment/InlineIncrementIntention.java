// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.increment;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class InlineIncrementIntention extends MutablyNamedIntention {
  @Override
  protected String getTextForElement(PsiElement element) {
    final String operator = IncrementUtil.getOperatorText(element);
    return operator != null ? IntentionPowerPackBundle.message(
      "inline.increment.intention.name", operator) : null;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiReferenceExpression operandExpression = IncrementUtil.getIncrementOrDecrementOperand(element);
    final PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(element.getParent(), PsiExpressionStatement.class);
    final String operatorText = IncrementUtil.getOperatorText(element);
    if (operandExpression != null && expressionStatement != null && operatorText != null) {
      final PsiVariable variable = resolveSimpleVariableReference(operandExpression);
      if (variable != null) {
        final Occurrence occurrence = findSingleReadOccurrence(expressionStatement, variable);
        if (occurrence != null) {
          final Project project = expressionStatement.getProject();
          final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          final String text = occurrence.isPrevious
                              ? occurrence.referenceExpression.getText() + operatorText
                              : operatorText + occurrence.referenceExpression.getText();
          PsiExpression incrementOrDecrement = factory.createExpressionFromText(text, expressionStatement);
          incrementOrDecrement = (PsiExpression)occurrence.referenceExpression.replace(incrementOrDecrement);
          final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
          codeStyle.reformat(incrementOrDecrement, true);
          final CommentTracker ct = new CommentTracker();
          ct.deleteAndRestoreComments(expressionStatement);
        }
      }
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return InlineIncrementIntention::isApplicableTo;
  }

  private static boolean isApplicableTo(PsiElement element) {
    final PsiReferenceExpression operandExpression = IncrementUtil.getIncrementOrDecrementOperand(element);
    final PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(element.getParent(), PsiExpressionStatement.class);
    if (operandExpression != null && expressionStatement != null) {
      final PsiVariable variable = resolveSimpleVariableReference(operandExpression);
      if (variable != null) {
        if (findSingleReadOccurrence(expressionStatement, variable) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  @Contract("_, null -> null")
  private static Occurrence findSingleReadOccurrence(@NotNull PsiExpressionStatement statement, @Nullable PsiVariable variable) {
    if (variable == null) return null;
    final PsiElement parent = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class, PsiLambdaExpression.class);
    if (parent instanceof PsiCodeBlock) {
      final PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      if (prevStatement instanceof PsiExpressionStatement || nextStatement instanceof PsiExpressionStatement) {
        final ControlFlow flow = getControlFlow(parent);

        if (prevStatement instanceof PsiExpressionStatement) {
          final PsiReferenceExpression prevOccurrence = ControlFlowUtil.findSingleReadOccurrence(flow, prevStatement, variable);
          if (prevOccurrence != null) {
            return new Occurrence(prevOccurrence, true);
          }
        }
        if (nextStatement instanceof PsiExpressionStatement) {
          final PsiReferenceExpression nextOccurrence = ControlFlowUtil.findSingleReadOccurrence(flow, nextStatement, variable);
          if (nextOccurrence != null) {
            return new Occurrence(nextOccurrence, false);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiVariable resolveSimpleVariableReference(@NotNull PsiReferenceExpression expression) {
    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null ||
        qualifierExpression instanceof PsiThisExpression ||
        qualifierExpression instanceof PsiSuperExpression) {
      final PsiElement resolved = expression.resolve();
      return resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
    }
    return null;
  }

  @NotNull
  public static ControlFlow getControlFlow(@NotNull PsiElement body) {
    try {
      final LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
      return ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, policy, false, false);
    }
    catch (AnalysisCanceledException e) {
      return ControlFlow.EMPTY;
    }
  }

  private static final class Occurrence {
    @NotNull final PsiReferenceExpression referenceExpression;
    final boolean isPrevious;

    private Occurrence(@NotNull PsiReferenceExpression referenceExpression, boolean isPrevious) {
      this.referenceExpression = referenceExpression;
      this.isPrevious = isPrevious;
    }
  }
}
