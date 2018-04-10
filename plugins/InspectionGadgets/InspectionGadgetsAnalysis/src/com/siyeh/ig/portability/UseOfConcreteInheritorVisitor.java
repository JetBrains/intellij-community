// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.portability;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class UseOfConcreteInheritorVisitor extends BaseInspectionVisitor {

  private final String myAncestorName;

  public UseOfConcreteInheritorVisitor(String ancestorName) {
    myAncestorName = ancestorName;
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    super.visitTypeElement(type);
    if (!usesAWTPeerClass(type.getType())) {
      return;
    }
    registerError(type);
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
    super.visitNewExpression(newExpression);
    if (!usesAWTPeerClass(newExpression.getType())) {
      return;
    }
    registerNewExpressionError(newExpression);
  }

  private boolean usesAWTPeerClass(PsiType type) {
    final PsiClass resolveClass = PsiUtil.resolveClassInType(type);
    return resolveClass != null &&
           !resolveClass.isEnum() &&
           !resolveClass.isAnnotationType() &&
           !resolveClass.isInterface() &&
           !(resolveClass instanceof PsiTypeParameter) &&
           InheritanceUtil.isInheritor(resolveClass, myAncestorName);
  }
}
