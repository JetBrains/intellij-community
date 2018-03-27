// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a model of PsiExpression which checks whether two expressions are equal (via Object.equals directly or indirectly).
 */
public class EqualityCheck {
  private static final CallMatcher OBJECT_EQUALS = CallMatcher.anyOf(
    CallMatcher.staticCall("java.util.Objects", "equals").parameterCount(2),
    CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));
  private final @NotNull PsiExpression myLeft;
  private final @NotNull PsiExpression myRight;
  private final boolean myLeftDereferenced;

  private EqualityCheck(@NotNull PsiExpression left, @NotNull PsiExpression right, boolean leftDereferenced) {
    myLeft = left;
    myRight = right;
    myLeftDereferenced = leftDereferenced;
  }

  /**
   * @param expression to create an {@link EqualityCheck} from
   * @return an {@link EqualityCheck} which represents an equality check performed on given expression; null if equality check
   * was not found in given expression.
   */
  @Nullable
  @Contract("null -> null")
  public static EqualityCheck from(PsiExpression expression) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
    if (call == null) {
      return null;
    }
    if (MethodCallUtils.isEqualsCall(call)) {
      PsiExpression left = call.getMethodExpression().getQualifierExpression();
      PsiExpression right = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (left == null || right == null) return null;
      return new EqualityCheck(left, right, true);
    }
    if (OBJECT_EQUALS.test(call)) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      return new EqualityCheck(args[0], args[1], false);
    }
    return null;
  }

  @NotNull
  public PsiExpression getLeft() {
    return myLeft;
  }

  @NotNull
  public PsiExpression getRight() {
    return myRight;
  }

  public boolean isLeftDereferenced() {
    return myLeftDereferenced;
  }
}
