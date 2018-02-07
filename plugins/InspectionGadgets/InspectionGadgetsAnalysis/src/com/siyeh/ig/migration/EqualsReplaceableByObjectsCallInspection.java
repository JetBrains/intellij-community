/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class EqualsReplaceableByObjectsCallInspection extends BaseInspection {
  public boolean checkNotNull;

  private static final EquivalenceChecker EQUIVALENCE = new NoSideEffectExpressionEquivalenceChecker();

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("equals.replaceable.by.objects.check.not.null.option"), this, "checkNotNull");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualsReplaceableByObjectsCallFix((String)infos[0], (String)infos[1], (Boolean)infos[2]);
  }

  private static class EqualsReplaceableByObjectsCallFix extends InspectionGadgetsFix {

    private final String myName1;
    private final String myName2;
    private final Boolean myEquals;

    public EqualsReplaceableByObjectsCallFix(String name1, String name2, Boolean equals) {
      myName1 = name1;
      myName2 = name2;
      myEquals = equals;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression ||
            element instanceof PsiMethodCallExpression ||
            element instanceof PsiConditionalExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      final String expressionText = "java.util.Objects.equals(" + myName1 + "," + myName2 + ")";
      PsiReplacementUtil.replaceExpressionAndShorten(expression, myEquals ? expressionText : "!" + expressionText, new CommentTracker());
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsReplaceableByObjectsCallVisitor();
  }

  private class EqualsReplaceableByObjectsCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final String methodName = expression.getMethodExpression().getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiExpression qualifierExpression = getQualifierExpression(expression);
      if (qualifierExpression instanceof PsiThisExpression || qualifierExpression instanceof PsiSuperExpression) {
        return;
      }
      if (isNotNullExpressionOrConstant(qualifierExpression)) {
        return;
      }
      final PsiElement parentExpression =
        PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class, PsiPrefixExpression.class);
      if (parentExpression instanceof PsiBinaryExpression) {
        if (processNotNullCheck((PsiBinaryExpression)parentExpression)) {
          return;
        }
      }
      else if (parentExpression instanceof PsiConditionalExpression) {
        if (processNotNullCondition((PsiConditionalExpression)parentExpression)) {
          return;
        }
      }
      if (!checkNotNull) {
        if (qualifierExpression == null) {
          return;
        }
        final PsiExpression argumentExpression = getArgumentExpression(expression);
        if (argumentExpression == null) {
          return;
        }
        registerError(expression, qualifierExpression.getText(), argumentExpression.getText(), true);
      }
    }

    private boolean processNotNullCheck(PsiBinaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      final PsiExpression rightOperand = ParenthesesUtils.stripParentheses(expression.getROperand());
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        return registerProblem(expression, rightOperand, true);
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        if (rightOperand instanceof PsiPrefixExpression &&
            JavaTokenType.EXCL.equals(((PsiPrefixExpression)rightOperand).getOperationTokenType())) {
          final PsiExpression negatedRightOperand = ParenthesesUtils.stripParentheses(((PsiPrefixExpression)rightOperand).getOperand());
          return registerProblem(expression, negatedRightOperand, false);
        }
      }
      return true;
    }

    /**
     * Report null-safe 'equals' checks in the form of ternary operator:
     * <ul>
     * <li>A == null ? B == null : A.equals(B) ~ equals(A, B)</li>
     * <li>A == null ? B != null : !A.equals(B) ~ !equals(A, B)</li>
     * <li>A != null ? A.equals(B) : B == null ~ equals(A, B)</li>
     * <li>A != null ? !A.equals(B) : B != null ~ !equals(A, B)</li>
     * </ul>
     *
     * @return true if such 'equals' check is found
     */
    private boolean processNotNullCondition(@NotNull PsiConditionalExpression expression) {
      final NullCheck conditionNullCheck = NullCheck.create(expression.getCondition());
      if (conditionNullCheck == null) return false;

      final PsiExpression nullBranch = conditionNullCheck.isEqual ? expression.getThenExpression() : expression.getElseExpression();
      final PsiExpression nonNullBranch = conditionNullCheck.isEqual ? expression.getElseExpression() : expression.getThenExpression();

      final NullCheck otherNullCheck = NullCheck.create(nullBranch);
      final EqualsCheck equalsCheck = EqualsCheck.create(nonNullBranch);
      if (otherNullCheck == null || equalsCheck == null || otherNullCheck.isEqual != equalsCheck.isEqual) return false;

      if (EQUIVALENCE.expressionsAreEquivalent(conditionNullCheck.compared, equalsCheck.qualifier) &&
          EQUIVALENCE.expressionsAreEquivalent(otherNullCheck.compared, equalsCheck.argument)) {
        registerError(expression, equalsCheck.qualifier.getText(), equalsCheck.argument.getText(), Boolean.valueOf(equalsCheck.isEqual));
        return true;
      }

      return false;
    }

    /**
     * Match the patterns, and register the error if a pattern is matched:
     * <pre>
     * x==null || !x.equals(y)
     * x!=null && x.equals(y)</pre>
     *
     * @return true if the pattern is matched
     */
    private boolean registerProblem(@NotNull PsiBinaryExpression expression, PsiExpression rightOperand, boolean equal) {
      if ((rightOperand instanceof PsiMethodCallExpression)) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)rightOperand;
        final NullCheck nullCheck = NullCheck.create(expression.getLOperand());
        if (nullCheck != null && nullCheck.isEqual != equal) {
          final PsiExpression nullCheckedExpression = nullCheck.compared;
          final PsiExpression qualifierExpression = getQualifierExpression(methodCallExpression);
          if (EQUIVALENCE.expressionsAreEquivalent(qualifierExpression, nullCheckedExpression)) {
            final PsiExpression argumentExpression = getArgumentExpression(methodCallExpression);
            if (argumentExpression != null) {
              final PsiExpression expressionToReplace = checkEqualityBefore(expression, equal, qualifierExpression, argumentExpression);
              registerError(expressionToReplace, nullCheckedExpression.getText(), argumentExpression.getText(), Boolean.valueOf(equal));
              return true;
            }
          }
        }
      }
      return false;
    }

    /**
     * Match the left side of the patterns:
     * <pre>
     * x!=y && (x==null || !x.equals(y))
     * x==y || (x!=null && x.equals(y))</pre>
     *
     * @return the expression matching the pattern, or the original expression if there's no match
     */
    @NotNull
    private PsiExpression checkEqualityBefore(@NotNull PsiExpression expression, boolean equal, PsiExpression part1, PsiExpression part2) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (equal && JavaTokenType.OROR.equals(tokenType) || !equal && JavaTokenType.ANDAND.equals(tokenType)) {
          if (PsiTreeUtil.isAncestor(binaryExpression.getROperand(), expression, false)) {
            final PsiExpression lhs = binaryExpression.getLOperand();
            if (isEquality(lhs, equal, part1, part2)) {
              return binaryExpression;
            }
          }
        }
      }
      return expression;
    }

    private boolean isEquality(PsiExpression expression, boolean equals, PsiExpression part1, PsiExpression part2) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      if (equals) {
        if (!JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      else {
        if (!JavaTokenType.NE.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      final PsiExpression leftOperand = binaryExpression.getLOperand();
      final PsiExpression rightOperand = binaryExpression.getROperand();
      return EQUIVALENCE.expressionsAreEquivalent(leftOperand, part1) && EQUIVALENCE.expressionsAreEquivalent(rightOperand, part2) ||
             EQUIVALENCE.expressionsAreEquivalent(leftOperand, part2) && EQUIVALENCE.expressionsAreEquivalent(rightOperand, part1);
    }
  }

  private static boolean isNotNullExpressionOrConstant(PsiExpression expression) {
    int preventEndlessLoop = 5;
    expression = ParenthesesUtils.stripParentheses(expression);
    while (expression instanceof PsiReferenceExpression) {
      if (--preventEndlessLoop == 0) return false;
      expression = findFinalVariableDefinition((PsiReferenceExpression)expression);
    }
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiArrayInitializerExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    return PsiUtil.isConstantExpression(expression);
  }

  @Nullable
  private static PsiExpression findFinalVariableDefinition(@NotNull PsiReferenceExpression expression) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)resolved;
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return ParenthesesUtils.stripParentheses(variable.getInitializer());
      }
    }
    return null;
  }

  private static PsiExpression getArgumentExpression(PsiMethodCallExpression callExpression) {
    final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    return expressions.length == 1 ? ParenthesesUtils.stripParentheses(expressions[0]) : null;
  }

  private static PsiExpression getQualifierExpression(PsiMethodCallExpression expression) {
    return ParenthesesUtils.stripParentheses(expression.getMethodExpression().getQualifierExpression());
  }

  //<editor-fold desc="Helpers">
  private static class Negated {
    @NotNull final PsiExpression expression;
    final boolean isEqual;

    public Negated(@NotNull PsiExpression expression, boolean isEqual) {
      this.expression = expression;
      this.isEqual = isEqual;
    }

    @Nullable
    static Negated create(@Nullable PsiExpression maybeNegatedExpression) {
      boolean equal = true;
      PsiExpression expression = ParenthesesUtils.stripParentheses(maybeNegatedExpression);
      if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        if (JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          equal = false;
          expression = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
        }
      }
      return expression != null ? new Negated(expression, equal) : null;
    }
  }

  private static class NullCheck {
    @NotNull final PsiExpression compared;
    final boolean isEqual;

    public NullCheck(@NotNull PsiExpression compared, boolean isEqual) {
      this.compared = compared;
      this.isEqual = isEqual;
    }

    @Nullable
    private static NullCheck create(@Nullable PsiExpression maybeNullCheckExpression) {
      final Negated n = Negated.create(maybeNullCheckExpression);
      if (n != null && n.expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)n.expression;
        PsiExpression comparedWithNull = ParenthesesUtils.stripParentheses(ExpressionUtils.getValueComparedWithNull(binaryExpression));
        if (comparedWithNull != null) {
          boolean equal = JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType());
          return new NullCheck(comparedWithNull, equal == n.isEqual);
        }
      }
      return null;
    }
  }

  private static class EqualsCheck {
    @NotNull final PsiExpression argument;
    @NotNull final PsiExpression qualifier;
    final boolean isEqual;

    public EqualsCheck(@NotNull PsiExpression argument, @NotNull PsiExpression qualifier, boolean isEqual) {
      this.argument = argument;
      this.qualifier = qualifier;
      this.isEqual = isEqual;
    }

    @Nullable
    private static EqualsCheck create(@Nullable PsiExpression maybeEqualsCheckExpression) {
      final Negated n = Negated.create(maybeEqualsCheckExpression);
      if (n != null && n.expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = ((PsiMethodCallExpression)n.expression);
        if (HardcodedMethodConstants.EQUALS.equals(callExpression.getMethodExpression().getReferenceName())) {
          final PsiExpression argument = getArgumentExpression(callExpression);
          final PsiExpression qualifier = getQualifierExpression(callExpression);
          if (argument != null && qualifier != null) {
            return new EqualsCheck(argument, qualifier, n.isEqual);
          }
        }
      }
      return null;
    }
  }

  private static class NoSideEffectExpressionEquivalenceChecker extends EquivalenceChecker {
    @Override
    protected Match newExpressionsMatch(@NotNull PsiNewExpression newExpression1,
                                        @NotNull PsiNewExpression newExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match methodCallExpressionsMatch(@NotNull PsiMethodCallExpression methodCallExpression1,
                                               @NotNull PsiMethodCallExpression methodCallExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match assignmentExpressionsMatch(@NotNull PsiAssignmentExpression assignmentExpression1,
                                               @NotNull PsiAssignmentExpression assignmentExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match arrayInitializerExpressionsMatch(@NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
                                                     @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match prefixExpressionsMatch(@NotNull PsiPrefixExpression prefixExpression1,
                                           @NotNull PsiPrefixExpression prefixExpression2) {
      if (isSideEffectUnaryOperator(prefixExpression1.getOperationTokenType())) {
        return EXACT_MISMATCH;
      }
      return super.prefixExpressionsMatch(prefixExpression1, prefixExpression2);
    }

    @Override
    protected Match postfixExpressionsMatch(@NotNull PsiPostfixExpression postfixExpression1,
                                            @NotNull PsiPostfixExpression postfixExpression2) {
      if (isSideEffectUnaryOperator(postfixExpression1.getOperationTokenType())) {
        return EXACT_MISMATCH;
      }
      return super.postfixExpressionsMatch(postfixExpression1, postfixExpression2);
    }

    private static boolean isSideEffectUnaryOperator(IElementType tokenType) {
      return JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType);
    }
  }
  //</editor-fold>
}
