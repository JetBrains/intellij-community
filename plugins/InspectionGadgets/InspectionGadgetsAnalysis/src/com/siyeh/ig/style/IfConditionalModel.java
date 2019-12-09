// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.stripBraces;
import static com.siyeh.ig.psiutils.ParenthesesUtils.stripParentheses;

/**
 * A model which represents an 'if' condition which could be replaced with '?:', '&&' or '||' expression
 */
public class IfConditionalModel extends ConditionalModel {

  @NotNull private final PsiStatement myThenBranch;
  @NotNull private final PsiStatement myElseBranch;

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
  @NotNull
  public PsiStatement getThenBranch() {
    return myThenBranch;
  }

  /**
   * Else branch of if statement. In case when else branch is missing statement after if statement is used.
   *
   * @return else branch
   */
  @NotNull
  public PsiStatement getElseBranch() {
    return myElseBranch;
  }

  /**
   * Converts if statement to conditional model.
   * This conversion is possible when both branches contain only one statement or else branch is missing
   * and then branch completes abruptly (e.g. with return statement).
   * Note that model might include surrounding statements like return which follows the if statement (in case when else branch is missing).
   *
   * @param ifStatement if statement
   * @return null if statement can't be converted, model otherwise
   */
  @Nullable
  public static IfConditionalModel from(@NotNull PsiIfStatement ifStatement) {
    IfConditionalModel model;
    model = extractFromAssignment(ifStatement);
    if (model != null) return model;
    model = extractFromImplicitReturn(ifStatement);
    if (model != null) return model;
    model = extractFromReturn(ifStatement);
    if (model != null) return model;
    model = extractFromYield(ifStatement);
    if (model != null) return model;
    model = extractFromImplicitYield(ifStatement);
    if (model != null) return model;
    return extractFromMethodCall(ifStatement);
  }

  @Nullable
  private static IfConditionalModel extractFromAssignment(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
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
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    IElementType elseTokenType = elseExpression.getOperationTokenType();
    final boolean operationIsOpposite = areOppositeAssignmentTokens(thenTokenType, elseTokenType);
    // Will be warned about equivalent if branches; no need to add ?: warning as well
    if (!operationIsOpposite && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenRhs, elseRhs)) return null;
    if (!(thenTokenType.equals(elseTokenType) || operationIsOpposite)) return null;

    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) return null;
    PsiType type = thenLhs.getType();
    if (type == null) return null;
    final PsiExpression resultElseRhs = getResultElseRhs(ifStatement, elseRhs, operationIsOpposite);
    return new IfConditionalModel(condition, thenRhs, resultElseRhs, thenBranch, elseBranch, type);
  }

  private static PsiExpression getResultElseRhs(@NotNull PsiIfStatement ifStatement,
                                                PsiExpression elseRhs,
                                                boolean operationIsOpposite) {
    if (!operationIsOpposite) return elseRhs;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(ifStatement.getProject());
    final String elseRhsText;
    if (elseRhs instanceof PsiPrefixExpression && ((PsiPrefixExpression)elseRhs).getOperationTokenType().equals(JavaTokenType.MINUS)) {
      final PsiExpression operand = ((PsiPrefixExpression)elseRhs).getOperand();
      if (operand != null) {
        return operand;
      }
      elseRhsText = "-(" + elseRhs.getText() + ")";
    } else {
      elseRhsText = "-" + elseRhs.getText();
    }
    return factory.createExpressionFromText(elseRhsText, elseRhs);
  }

  static boolean areOppositeAssignmentTokens(IElementType thenTokenType, IElementType elseTokenType) {
    return thenTokenType.equals(JavaTokenType.PLUSEQ) && elseTokenType.equals(JavaTokenType.MINUSEQ) ||
                                        thenTokenType.equals(JavaTokenType.MINUSEQ) && elseTokenType.equals(JavaTokenType.PLUSEQ);
  }

  @Nullable
  private static IfConditionalModel extractFromReturn(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
    if (condition == null) return null;
    PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
    PsiReturnStatement elseBranch = tryCast(stripBraces(ifStatement.getElseBranch()), PsiReturnStatement.class);
    return extractFromReturn(condition, thenBranch, elseBranch);
  }

  @Nullable
  private static IfConditionalModel extractFromImplicitReturn(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
    if (condition == null) return null;
    if (ifStatement.getElseBranch() != null) return null;
    PsiReturnStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiReturnStatement.class);
    PsiReturnStatement nextReturnStatement =
      tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiReturnStatement.class);
    return extractFromReturn(condition, thenBranch, nextReturnStatement);
  }

  @Nullable
  private static IfConditionalModel extractFromImplicitYield(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
    if (condition == null) return null;
    if (ifStatement.getElseBranch() != null) return null;
    PsiYieldStatement thenBranch = tryCast(stripBraces(ifStatement.getThenBranch()), PsiYieldStatement.class);
    PsiYieldStatement nextReturnStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiYieldStatement.class);
    return extractFromYield(condition, thenBranch, nextReturnStatement);
  }

  @Nullable
  private static IfConditionalModel extractFromYield(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
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
  @Nullable
  private static IfConditionalModel extractFromReturn(PsiExpression condition,
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

  @Nullable
  private static IfConditionalModel extractFromMethodCall(@NotNull PsiIfStatement ifStatement) {
    PsiExpression condition = stripParentheses(ifStatement.getCondition());
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
