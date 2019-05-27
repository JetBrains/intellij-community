// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class SimplifiableIfStatementInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean DONT_WARN_ON_TERNARY = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "inspection.simplifiable.if.statement.option.dont.warn.on.ternary"), this,
                                          "DONT_WARN_ON_TERNARY");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.display.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement ifStatement) {
        IfConditionalModel model = IfConditionalModel.from(ifStatement);
        if (model == null) return;
        String operator = getTargetOperator(model);
        if (operator.isEmpty()) return;
        boolean infoLevel = DONT_WARN_ON_TERNARY && operator.equals("?:");
        if (!isOnTheFly && infoLevel) return;
        holder.registerProblem(ifStatement.getFirstChild(),
                               InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.message", operator),
                               infoLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new SimplifiableIfStatementFix(operator));
      }
    };
  }

  @NotNull
  private static String getTargetOperator(@NotNull ConditionalModel model) {
    if (PsiType.BOOLEAN.equals(model.getType())) {
      PsiLiteralExpression thenLiteral = ExpressionUtils.getLiteral(model.getThenExpression());
      PsiLiteralExpression elseLiteral = ExpressionUtils.getLiteral(model.getElseExpression());
      Boolean thenValue = thenLiteral == null ? null : tryCast(thenLiteral.getValue(), Boolean.class);
      Boolean elseValue = elseLiteral == null ? null : tryCast(elseLiteral.getValue(), Boolean.class);
      if (thenValue != null && elseValue != null) {
        // either both branches are equal or can be simplified to "condition" or "!condition" - another inspection will take care of this
        return "";
      }
      Boolean value = thenValue == null ? elseValue : thenValue;
      if (value != null) {
        return value ? "||" : "&&";
      }
    }
    return "?:";
  }

  private static String buildExpressionText(@NotNull ConditionalModel model, CommentTracker ct) {
    PsiExpression condition = ParenthesesUtils.stripParentheses(model.getCondition());
    if (condition == null) return null;
    PsiExpression thenValue = ParenthesesUtils.stripParentheses(model.getThenExpression());
    PsiExpression elseValue = ParenthesesUtils.stripParentheses(model.getElseExpression());

    thenValue = expandDiamondsWhenNeeded(thenValue, model.getType());
    elseValue = expandDiamondsWhenNeeded(elseValue, model.getType());
    if (thenValue == null || elseValue == null) return null;

    if (PsiType.BOOLEAN.equals(model.getType())) {
      PsiLiteralExpression thenLiteral = ExpressionUtils.getLiteral(thenValue);
      PsiLiteralExpression elseLiteral = ExpressionUtils.getLiteral(elseValue);
      Boolean thenBoolean = thenLiteral == null ? null : tryCast(thenLiteral.getValue(), Boolean.class);
      Boolean elseBoolean = elseLiteral == null ? null : tryCast(elseLiteral.getValue(), Boolean.class);
      if (thenBoolean != null) {
        return thenBoolean ?
               joinConditions(condition, elseValue, false, ct) :
               BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE, ct) + "&&" +
               ct.text(elseValue, ParenthesesUtils.AND_PRECEDENCE);
      }
      if (elseBoolean != null) {
        return elseBoolean
               ? BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE, ct) + "||" +
                 ct.text(thenValue, ParenthesesUtils.OR_PRECEDENCE)
               : joinConditions(condition, thenValue, true, ct);
      }
    }
    @NonNls final StringBuilder conditional = new StringBuilder();
    final String conditionText = ct.text(condition, ParenthesesUtils.CONDITIONAL_PRECEDENCE);
    if (condition instanceof PsiConditionalExpression) {
      conditional.append('(').append(conditionText).append(')');
    }
    else {
      conditional.append(conditionText);
    }
    conditional.append('?');
    final PsiType thenType = thenValue.getType();
    final PsiType elseType = elseValue.getType();
    if (thenType instanceof PsiPrimitiveType &&
        !PsiType.NULL.equals(thenType) &&
        !(elseType instanceof PsiPrimitiveType) &&
        !(model.getType() instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType)thenType;
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(ct.text(thenValue)).append("):");
      conditional.append(ct.text(elseValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
    }
    else if (elseType instanceof PsiPrimitiveType &&
             !PsiType.NULL.equals(elseType) &&
             !(thenType instanceof PsiPrimitiveType) &&
             !(model.getType() instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
      conditional.append(ct.text(thenValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
      conditional.append(':');
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType)elseType;
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(ct.text(elseValue)).append(')');
    }
    else {
      conditional.append(ct.text(thenValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
      conditional.append(':');
      conditional.append(ct.text(elseValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
    }
    return conditional.toString();
  }

  public static void tryJoinDeclaration(PsiElement result) {
    if (!(result instanceof PsiExpressionStatement)) return;
    PsiAssignmentExpression assignment = tryCast(((PsiExpressionStatement)result).getExpression(), PsiAssignmentExpression.class);
    if (assignment == null) return;
    if (!assignment.getOperationTokenType().equals(JavaTokenType.EQ)) return;
    PsiReferenceExpression ref = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (ref == null) return;
    PsiDeclarationStatement declaration = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(result), PsiDeclarationStatement.class);
    if (declaration == null) return;
    PsiElement[] elements = declaration.getDeclaredElements();
    if (elements.length != 1) return;
    PsiLocalVariable var = tryCast(elements[0], PsiLocalVariable.class);
    if (var == null || var.getInitializer() != null || !ref.isReferenceTo(var)) return;
    CommentTracker ct = new CommentTracker();
    var.setInitializer(ct.markUnchanged(assignment.getRExpression()));
    ct.deleteAndRestoreComments(result);
  }

  private static PsiExpression expandDiamondsWhenNeeded(PsiExpression thenValue, PsiType requiredType) {
    if (thenValue instanceof PsiNewExpression) {
      if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression)thenValue, requiredType)) {
        return PsiDiamondTypeUtil.expandTopLevelDiamondsInside(thenValue);
      }
    }
    return thenValue;
  }

  @NotNull
  private static String joinConditions(PsiExpression left, PsiExpression right, boolean isAnd, CommentTracker ct) {
    int precedence;
    String token;
    IElementType tokenType;
    if (isAnd) {
      precedence = ParenthesesUtils.AND_PRECEDENCE;
      token = "&&";
      tokenType = JavaTokenType.ANDAND;
    }
    else {
      precedence = ParenthesesUtils.OR_PRECEDENCE;
      token = "||";
      tokenType = JavaTokenType.OROR;
    }
    PsiPolyadicExpression leftPolyadic = tryCast(PsiUtil.skipParenthesizedExprDown(left), PsiPolyadicExpression.class);
    PsiPolyadicExpression rightPolyadic = tryCast(PsiUtil.skipParenthesizedExprDown(right), PsiPolyadicExpression.class);
    // foo && (foo && bar) -> foo && bar
    if (rightPolyadic != null && rightPolyadic.getOperationTokenType().equals(tokenType) &&
        EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(ArrayUtil.getFirstElement(rightPolyadic.getOperands()), left) &&
        !SideEffectChecker.mayHaveSideEffects(left)) {
      return ct.text(rightPolyadic);
    }
    // (foo && bar) && bar -> foo && bar
    if (leftPolyadic != null && leftPolyadic.getOperationTokenType().equals(tokenType) &&
        EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(ArrayUtil.getLastElement(leftPolyadic.getOperands()), right) &&
        !SideEffectChecker.mayHaveSideEffects(right)) {
      return ct.text(leftPolyadic);
    }
    return ct.text(left, precedence) + token + ct.text(right, precedence);
  }

  private static class SimplifiableIfStatementFix implements LocalQuickFix {
    private final String myOperator;

    SimplifiableIfStatementFix(String operator) {
      myOperator = operator;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.fix.name", myOperator);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class);
      if (ifStatement == null) return;

      IfConditionalModel model = IfConditionalModel.from(ifStatement);
      if (model == null) return;
      CommentTracker commentTracker = new CommentTracker();
      String conditional = buildExpressionText(model, commentTracker);
      if (conditional == null) return;
      commentTracker.replace(model.getThenExpression(), conditional);
      if (!PsiTreeUtil.isAncestor(ifStatement, model.getElseBranch(), true)) {
        commentTracker.delete(model.getElseBranch());
      }
      PsiElement result = commentTracker.replaceAndRestoreComments(ifStatement, model.getThenBranch());
      tryJoinDeclaration(result);
    }
  }
}
