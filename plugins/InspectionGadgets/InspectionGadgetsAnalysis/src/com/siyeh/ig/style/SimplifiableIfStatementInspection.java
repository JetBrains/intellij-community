// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.stripBraces;

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
        ReplaceIfWithConditionalModel model = ReplaceIfWithConditionalModel.from(ifStatement);
        if (model == null) return;
        String operator = model.getTargetOperator();
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

  private static class SimplifiableIfStatementFix implements LocalQuickFix {
    private final String myOperator;

    public SimplifiableIfStatementFix(String operator) {
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

      ReplaceIfWithConditionalModel model = ReplaceIfWithConditionalModel.from(ifStatement);
      if (model == null) return;
      CommentTracker commentTracker = new CommentTracker();
      String conditional = model.buildExpressionText(commentTracker);
      if (conditional == null) return;
      commentTracker.replace(model.myThenExpression, conditional);
      if (!PsiTreeUtil.isAncestor(ifStatement, model.myElseBranch, true)) {
        commentTracker.delete(model.myElseBranch);
      }
      PsiElement result = commentTracker.replaceAndRestoreComments(ifStatement, model.myThenBranch);
      tryJoinDeclaration(result);
    }

    private static void tryJoinDeclaration(PsiElement result) {
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
      var.setInitializer(ct.markUnchanged(Objects.requireNonNull(assignment.getRExpression())));
      ct.deleteAndRestoreComments(result);
    }
  }

  /**
   * A model which represents an 'if' condition which could be replaced with '?:', '&&' or '||' expression
   */
  static class ReplaceIfWithConditionalModel {
    @NotNull final PsiExpression myCondition;
    @NotNull final PsiExpression myThenExpression;
    @NotNull final PsiExpression myElseExpression;
    @NotNull final PsiStatement myThenBranch;
    @NotNull final PsiStatement myElseBranch;
    @NotNull final PsiType myType;

    ReplaceIfWithConditionalModel(@NotNull PsiExpression condition,
                                  @NotNull PsiExpression thenExpression,
                                  @NotNull PsiExpression elseExpression,
                                  @NotNull PsiStatement thenBranch,
                                  @NotNull PsiStatement elseBranch,
                                  @NotNull PsiType type) {
      myCondition = condition;
      myThenExpression = thenExpression;
      myElseExpression = elseExpression;
      myThenBranch = thenBranch;
      myElseBranch = elseBranch;
      myType = type;
    }

    @NotNull
    String getTargetOperator() {
      if (PsiType.BOOLEAN.equals(myType)) {
        PsiLiteralExpression thenLiteral = ExpressionUtils.getLiteral(myThenExpression);
        PsiLiteralExpression elseLiteral = ExpressionUtils.getLiteral(myElseExpression);
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

    String buildExpressionText(CommentTracker ct) {
      PsiExpression condition = ParenthesesUtils.stripParentheses(myCondition);
      PsiExpression thenValue = ParenthesesUtils.stripParentheses(myThenExpression);
      PsiExpression elseValue = ParenthesesUtils.stripParentheses(myElseExpression);

      thenValue = expandDiamondsWhenNeeded(thenValue, myType);
      elseValue = expandDiamondsWhenNeeded(elseValue, myType);
      if (thenValue == null || elseValue == null) return null;

      if (PsiType.BOOLEAN.equals(myType)) {
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
          !(myType instanceof PsiPrimitiveType)) {
        // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
        final PsiPrimitiveType primitiveType = (PsiPrimitiveType)thenType;
        conditional.append(primitiveType.getBoxedTypeName());
        conditional.append(".valueOf(").append(ct.text(thenValue)).append("):");
        conditional.append(ct.text(elseValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
      }
      else if (elseType instanceof PsiPrimitiveType &&
               !PsiType.NULL.equals(elseType) &&
               !(thenType instanceof PsiPrimitiveType) &&
               !(myType instanceof PsiPrimitiveType)) {
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

    static ReplaceIfWithConditionalModel from(PsiIfStatement ifStatement) {
      ReplaceIfWithConditionalModel model;
      model = extractFromAssignment(ifStatement);
      if (model != null) return model;
      model = extractFromImplicitReturn(ifStatement);
      if (model != null) return model;
      model = extractFromReturn(ifStatement);
      if (model != null) return model;
      return extractFromMethodCall(ifStatement);
    }

    @Nullable
    private static ReplaceIfWithConditionalModel extractFromAssignment(PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      PsiExpressionStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiExpressionStatement.class);
      PsiExpressionStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiExpressionStatement.class);
      if (thenBranch == null || elseBranch == null) return null;
      PsiAssignmentExpression thenExpression = tryCast(thenBranch.getExpression(), PsiAssignmentExpression.class);
      PsiAssignmentExpression elseExpression = tryCast(elseBranch.getExpression(), PsiAssignmentExpression.class);
      if (thenExpression == null || elseExpression == null) return null;
      PsiExpression thenRhs = thenExpression.getRExpression();
      PsiExpression elseRhs = elseExpression.getRExpression();
      if (thenRhs == null || elseRhs == null) return null;
      // Will be warned about equivalent if branches; no need to add ?: warning as well
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenRhs, elseRhs)) return null;
      IElementType thenTokenType = thenExpression.getOperationTokenType();
      IElementType elseTokenType = elseExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseTokenType)) return null;

      PsiExpression thenLhs = thenExpression.getLExpression();
      PsiExpression elseLhs = elseExpression.getLExpression();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) return null;
      PsiType type = thenLhs.getType();
      if (type == null) return null;
      return new ReplaceIfWithConditionalModel(condition, thenRhs, elseRhs, thenBranch, elseBranch, type);
    }

    @Nullable
    private static ReplaceIfWithConditionalModel extractFromReturn(PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
      PsiReturnStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiReturnStatement.class);
      return extractFromReturn(condition, thenBranch, elseBranch);
    }

    @Nullable
    private static ReplaceIfWithConditionalModel extractFromImplicitReturn(PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      if (ifStatement.getElseBranch() != null) return null;
      PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
      PsiReturnStatement nextReturnStatement =
        tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiReturnStatement.class);
      return extractFromReturn(condition, thenBranch, nextReturnStatement);
    }

    @Contract("_, null, _ -> null; _, !null, null -> null")
    @Nullable
    private static ReplaceIfWithConditionalModel extractFromReturn(PsiExpression condition,
                                                                   PsiReturnStatement thenBranch,
                                                                   PsiReturnStatement elseBranch) {
      if (thenBranch == null || elseBranch == null) return null;
      final PsiExpression thenReturn = thenBranch.getReturnValue();
      if (thenReturn == null) return null;
      final PsiExpression elseReturn = elseBranch.getReturnValue();
      if (elseReturn == null) return null;
      final PsiType thenType = thenReturn.getType();
      final PsiType elseType = elseReturn.getType();
      if (thenType == null || elseType == null) return null;
      if (!thenType.isAssignableFrom(elseType) && !elseType.isAssignableFrom(thenType)) return null;
      PsiType type = PsiTypesUtil.getMethodReturnType(thenReturn);
      if (type == null) return null;
      return new ReplaceIfWithConditionalModel(condition, thenReturn, elseReturn, thenBranch, elseBranch, type);
    }

    @Nullable
    private static ReplaceIfWithConditionalModel extractFromMethodCall(PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      PsiExpressionStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiExpressionStatement.class);
      PsiExpressionStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiExpressionStatement.class);
      if (thenBranch == null || elseBranch == null) return null;
      PsiMethodCallExpression thenCall = tryCast(thenBranch.getExpression(), PsiMethodCallExpression.class);
      PsiMethodCallExpression elseCall = tryCast(elseBranch.getExpression(), PsiMethodCallExpression.class);
      if (thenCall == null || elseCall == null) return null;
      PsiReferenceExpression thenMethodExpression = thenCall.getMethodExpression();
      PsiReferenceExpression elseMethodExpression = elseCall.getMethodExpression();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenMethodExpression, elseMethodExpression)) {
        return null;
      }
      PsiMethod thenMethod = thenCall.resolveMethod();
      PsiMethod elseMethod = elseCall.resolveMethod();
      if (thenMethod == null || elseMethod == null || !thenMethod.equals(elseMethod)) return null;
      final PsiExpression[] thenArguments = thenCall.getArgumentList().getExpressions();
      final PsiExpression[] elseArguments = elseCall.getArgumentList().getExpressions();
      if (thenArguments.length != elseArguments.length) return null;
      PsiParameter[] parameterList = thenMethod.getParameterList().getParameters();
      if (parameterList.length > thenArguments.length) return null;
      boolean vararg = MethodCallUtils.isVarArgCall(thenCall);
      if (vararg != MethodCallUtils.isVarArgCall(elseCall)) return null;
      if (!vararg && parameterList.length != thenArguments.length) return null;
      ReplaceIfWithConditionalModel model = null;
      for (int i = 0; i < thenArguments.length; i++) {
        final PsiExpression thenArgument = thenArguments[i];
        final PsiExpression elseArgument = elseArguments[i];
        if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
          if (model != null) return null;
          PsiType type;
          int lastParameter = parameterList.length - 1;
          if (vararg && i >= lastParameter) {
            type = ((PsiEllipsisType)parameterList[lastParameter].getType()).getComponentType();
          }
          else {
            type = parameterList[i].getType();
          }
          model = new ReplaceIfWithConditionalModel(condition, thenArgument, elseArgument, thenBranch, elseBranch, type);
        }
      }
      return model;
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
  }
}
