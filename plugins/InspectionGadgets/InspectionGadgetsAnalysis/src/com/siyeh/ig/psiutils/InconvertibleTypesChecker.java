// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class InconvertibleTypesChecker {
  @Contract(pure = true)
  public static @Nullable TypeMismatch checkTypes(@NotNull PsiType leftType,
                                                  @NotNull PsiType rightType,
                                                  @NotNull LookForMutualSubclass lookForMutualSubclass) {
    if (TypeUtils.areConvertible(leftType, rightType) || TypeUtils.mayBeEqualByContract(leftType, rightType)) {
      return deepCheck(leftType, rightType, lookForMutualSubclass);
    }
    return new TypeMismatch(leftType, rightType, false);
  }

  public static @Nullable TypeMismatch deepCheck(@NotNull PsiType leftType,
                                                 @NotNull PsiType rightType,
                                                 @NotNull LookForMutualSubclass lookForMutualSubclass) {
    return deepCheck(leftType, rightType, new HashSet<>(), lookForMutualSubclass);
  }

  private static @Nullable TypeMismatch deepCheck(@NotNull PsiType leftType,
                                                  @NotNull PsiType rightType,
                                                  @NotNull Set<Couple<PsiType>> checked,
                                                  @NotNull LookForMutualSubclass lookForMutualSubclass) {
    if (leftType instanceof PsiCapturedWildcardType) {
      leftType = ((PsiCapturedWildcardType)leftType).getUpperBound();
    }
    if (rightType instanceof PsiCapturedWildcardType) {
      rightType = ((PsiCapturedWildcardType)rightType).getUpperBound();
    }
    if (!checked.add(Couple.of(leftType, rightType))) {
      return null;
    }
    if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) return null;
    PsiClass leftClass = PsiUtil.resolveClassInClassTypeOnly(leftType);
    PsiClass rightClass = PsiUtil.resolveClassInClassTypeOnly(rightType);
    if (leftClass == null || rightClass == null) return null;
    if (!rightClass.isInterface()) {
      PsiClass tmp = leftClass;
      leftClass = rightClass;
      rightClass = tmp;
    }
    if (leftClass == rightClass || TypeUtils.mayBeEqualByContract(leftType, rightType)) {
      // check type parameters
      if (leftType instanceof PsiClassType && rightType instanceof PsiClassType) {
        final PsiType[] leftParameters = ((PsiClassType)leftType).getParameters();
        final PsiType[] rightParameters = ((PsiClassType)rightType).getParameters();
        if (leftParameters.length == rightParameters.length) {
          for (int i = 0, length = leftParameters.length; i < length; i++) {
            final PsiType leftParameter = leftParameters[i];
            final PsiType rightParameter = rightParameters[i];
            if (!TypeUtils.areConvertible(leftParameter, rightParameter) &&
                !TypeUtils.mayBeEqualByContract(leftParameter, rightParameter)) {
              return new TypeMismatch(leftType, rightType, false);
            }
            TypeMismatch mismatch = deepCheck(leftParameter, rightParameter, checked, lookForMutualSubclass);
            if (mismatch != null) {
              return mismatch;
            }
          }
        }
      }
    }
    else if (TypeUtils.cannotBeEqualByContract(leftType, rightType)) {
      return new TypeMismatch(leftType, rightType, false);
    }
    else if (lookForMutualSubclass != LookForMutualSubclass.NEVER &&
             !InheritanceUtil.existsMutualSubclass(leftClass, rightClass, lookForMutualSubclass == LookForMutualSubclass.IF_CHEAP)) {
      return new TypeMismatch(leftType, rightType, true);
    }
    return null;
  }

  public enum LookForMutualSubclass {
    NEVER, ALWAYS, IF_CHEAP
  }

  public static final class TypeMismatch {
    private final @NotNull PsiType myLeft;
    private final @NotNull PsiType myRight;
    private final boolean myConvertible;

    private TypeMismatch(@NotNull PsiType left, @NotNull PsiType right, boolean convertible) {
      myLeft = left;
      myRight = right;
      myConvertible = convertible;
    }

    public @NotNull PsiType getLeft() {
      return myLeft;
    }

    public @NotNull PsiType getRight() {
      return myRight;
    }

    public boolean isConvertible() {
      return myConvertible;
    }
  }
}
