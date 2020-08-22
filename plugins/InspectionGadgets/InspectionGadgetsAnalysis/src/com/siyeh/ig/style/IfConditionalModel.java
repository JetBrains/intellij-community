// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.stripBraces;

/**
 * A model which represents an 'if' condition which could be replaced with '?:', '&&' or '||' expression
 */
public final class IfConditionalModel extends ConditionalModel {

  private final @NotNull PsiStatement myThenBranch;
  private final @NotNull PsiStatement myElseBranch;

  private IfConditionalModel(@NotNull PsiExpression condition,
                            @NotNull PsiExpression thenExpression,
                            @NotNull PsiExpression elseExpression,
                            @NotNull PsiStatement thenBranch,
                            @NotNull PsiStatement elseBranch,
                            @NotNull PsiType type) {
    super(condition, thenExpression, elseExpression, type);
    myThenBranch = thenBranch;
    myElseBranch = elseBranch;
  }

  /**
   * Then branch of if statement.
   *
   * @return then branch
   */
  public @NotNull PsiStatement getThenBranch() {
    return myThenBranch;
  }

  /**
   * Else branch of if statement. In case when else branch is missing statement after if statement is used.
   *
   * @return else branch
   */
  public @NotNull PsiStatement getElseBranch() {
    return myElseBranch;
  }

  /**
   * Converts if statement to conditional model.
   * This conversion is possible when both branches contain only one statement or else branch is missing
   * and then branch completes abruptly (e.g. with return statement).
   * Note that model might include surrounding statements like return which follows the if statement (in case when else branch is missing).
   *
   * @param ifStatement if statement
   * @param allowOuterControlFlow if true the model could be extracted even if the else branch could be reused in outer block. E.g.:
   * <pre>{@code if(foo) {
   *   if(bar) return "foobar";
   * }
   * return "none";}</pre>
   * The model for inner {@code if} could be extracted with {@code condition = bar; thenBranch = "foobar"; elseBranch = "none"} when
   * this parameter is true.
   * @return null if statement can't be converted, model otherwise
   */
  public static @Nullable IfConditionalModel from(@NotNull PsiIfStatement ifStatement, boolean allowOuterControlFlow) {
    IfConditionalModel model;
    model = extractFromAssignment(ifStatement);
    if (model != null) return model;
    model = extractFromImplicitAssignment(ifStatement);
    if (model != null) return model;
    model = extractFromImplicitReturn(ifStatement, allowOuterControlFlow);
    if (model != null) return model;
    model = extractFromReturn(ifStatement);
    if (model != null) return model;
    model = extractFromYield(ifStatement);
    if (model != null) return model;
    model = extractFromImplicitYield(ifStatement);
    if (model != null) return model;
    return extractFromMethodCall(ifStatement);
  }

  private static @Nullable IfConditionalModel extractFromImplicitAssignment(@NotNull PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) return null;
    PsiStatement prevStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(ifStatement), PsiStatement.class);
    return extractFromAssignment(ifStatement.getCondition(), ifStatement.getThenBranch(), prevStatement, false);
  }

  private static @Nullable IfConditionalModel extractFromAssignment(@NotNull PsiIfStatement ifStatement) {
    return extractFromAssignment(ifStatement.getCondition(), ifStatement.getThenBranch(), ifStatement.getElseBranch(), true);
  }

  private static @Nullable IfConditionalModel extractFromAssignment(@Nullable PsiExpression condition,
                                                                    @Nullable PsiStatement thenStatement,
                                                                    @Nullable PsiStatement elseStatement, boolean explicit) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (condition == null) return null;
    PsiExpressionStatement thenBranch = tryCast(stripBraces(thenStatement), PsiExpressionStatement.class);
    PsiExpressionStatement elseBranch = tryCast(stripBraces(elseStatement), PsiExpressionStatement.class);
    if (thenBranch == null || elseBranch == null) return null;
    PsiAssignmentExpression thenExpression = tryCast(thenBranch.getExpression(), PsiAssignmentExpression.class);
    PsiAssignmentExpression elseExpression = tryCast(elseBranch.getExpression(), PsiAssignmentExpression.class);
    if (thenExpression == null || elseExpression == null) return null;
    PsiExpression thenRhs = thenExpression.getRExpression();
    PsiExpression elseRhs = elseExpression.getRExpression();
    if (thenRhs == null || elseRhs == null) return null;
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    IElementType elseTokenType = elseExpression.getOperationTokenType();
    if (!explicit && !thenTokenType.equals(JavaTokenType.EQ)) return null;
    final boolean operationIsOpposite = areOppositeAssignmentTokens(thenTokenType, elseTokenType);
    // Will be warned about equivalent if branches; no need to add ?: warning as well
    if (!operationIsOpposite && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenRhs, elseRhs)) return null;
    if (!(thenTokenType.equals(elseTokenType) || operationIsOpposite)) return null;

    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) return null;
    PsiType type = thenLhs.getType();
    if (type == null) return null;
    final PsiExpression resultElseRhs = getResultElseRhs(elseRhs, operationIsOpposite);
    if (!explicit && !ExpressionUtils.isSafelyRecomputableExpression(elseRhs)) return null;
    return new IfConditionalModel(condition, thenRhs, resultElseRhs, thenBranch, elseBranch, type);
  }

  private static PsiExpression getResultElseRhs(@NotNull PsiExpression elseRhs, boolean operationIsOpposite) {
    if (!operationIsOpposite) return elseRhs;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(elseRhs.getProject());
    final String elseRhsText;
    if (elseRhs instanceof PsiPrefixExpression && ((PsiPrefixExpression)elseRhs).getOperationTokenType().equals(JavaTokenType.MINUS)) {
      final PsiExpression operand = ((PsiPrefixExpression)elseRhs).getOperand();
      if (operand != null) {
        return operand;
      }
    }
    elseRhsText = "-" + ParenthesesUtils.getText(elseRhs, ParenthesesUtils.ADDITIVE_PRECEDENCE);
    return factory.createExpressionFromText(elseRhsText, elseRhs);
  }

  static boolean areOppositeAssignmentTokens(IElementType thenTokenType, IElementType elseTokenType) {
    return thenTokenType.equals(JavaTokenType.PLUSEQ) && elseTokenType.equals(JavaTokenType.MINUSEQ) ||
                                        thenTokenType.equals(JavaTokenType.MINUSEQ) && elseTokenType.equals(JavaTokenType.PLUSEQ);
  }

  private static @Nullable IfConditionalModel extractFromReturn(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition == null) return null;
    PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
    PsiReturnStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiReturnStatement.class);
    return extractFromReturn(condition, thenBranch, elseBranch);
  }

  private static @Nullable IfConditionalModel extractFromImplicitReturn(@NotNull PsiIfStatement ifStatement, boolean allowReturnInOuterBranch) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition == null) return null;
    if (ifStatement.getElseBranch() != null) return null;
    PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
    PsiReturnStatement nextReturnStatement = allowReturnInOuterBranch ?
                                             ControlFlowUtils.getNextReturnStatement(ifStatement) :
                                             tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiReturnStatement.class);
    if (nextReturnStatement == null) return null;
    return extractFromReturn(condition, thenBranch, nextReturnStatement);
  }

  private static @Nullable IfConditionalModel extractFromImplicitYield(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition == null) return null;
    if (ifStatement.getElseBranch() != null) return null;
    PsiYieldStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiYieldStatement.class);
    PsiYieldStatement nextReturnStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiYieldStatement.class);
    return extractFromYield(condition, thenBranch, nextReturnStatement);
  }

  private static @Nullable IfConditionalModel extractFromYield(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition == null) return null;
    PsiYieldStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiYieldStatement.class);
    PsiYieldStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiYieldStatement.class);
    return extractFromYield(condition, thenBranch, elseBranch);
  }

  @Contract("_, null, _ -> null; _, !null, null -> null")
  private static IfConditionalModel extractFromYield(PsiExpression condition,
                                                     PsiYieldStatement thenBranch,
                                                     PsiYieldStatement elseBranch) {
    if (thenBranch == null || elseBranch == null) return null;
    final PsiExpression thenBreak = thenBranch.getExpression();
    if (thenBreak == null) return null;
    final PsiExpression elseBreak = elseBranch.getExpression();
    if (elseBreak == null) return null;
    PsiType type = getType(condition, thenBreak, elseBreak);
    if (type == null) return null;
    return new IfConditionalModel(condition, thenBreak, elseBreak, thenBranch, elseBranch, type);
  }

  @Contract("_, null, _ -> null; _, !null, null -> null")
  private static @Nullable IfConditionalModel extractFromReturn(PsiExpression condition,
                                                                PsiReturnStatement thenBranch,
                                                                PsiReturnStatement elseBranch) {
    if (thenBranch == null || elseBranch == null) return null;
    final PsiExpression thenReturn = thenBranch.getReturnValue();
    if (thenReturn == null) return null;
    final PsiExpression elseReturn = elseBranch.getReturnValue();
    if (elseReturn == null) return null;
    PsiType type = getType(condition, thenReturn, elseReturn);
    if (type == null) return null;
    return new IfConditionalModel(condition, thenReturn, elseReturn, thenBranch, elseBranch, type);
  }

  private static @Nullable IfConditionalModel extractFromMethodCall(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
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
    if (thenMethod == null || !thenMethod.equals(elseMethod)) return null;
    final PsiExpression[] thenArguments = thenCall.getArgumentList().getExpressions();
    final PsiExpression[] elseArguments = elseCall.getArgumentList().getExpressions();
    if (thenArguments.length != elseArguments.length) return null;
    PsiParameter[] parameterList = thenMethod.getParameterList().getParameters();
    if (parameterList.length > thenArguments.length) return null;
    boolean vararg = MethodCallUtils.isVarArgCall(thenCall);
    if (vararg != MethodCallUtils.isVarArgCall(elseCall)) return null;
    if (!vararg && parameterList.length != thenArguments.length) return null;
    IfConditionalModel model = null;
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
        model = new IfConditionalModel(condition, thenArgument, elseArgument, thenBranch, elseBranch, type);
      }
    }
    return model;
  }
}
