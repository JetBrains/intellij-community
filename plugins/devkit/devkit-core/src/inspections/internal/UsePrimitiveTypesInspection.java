// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public class UsePrimitiveTypesInspection extends DevKitUastInspectionBase {

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (JavaPsiFacade.getInstance(holder.getProject()).findClass(PsiType.class.getName(),
                                                                 holder.getFile().getResolveScope()) == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
        inspectBinaryExpression(node, holder);

        return true;
      }
    }, new Class[]{UBinaryExpression.class});
  }

  private static void inspectBinaryExpression(UBinaryExpression uBinaryExpression, ProblemsHolder holder) {
    final UastBinaryOperator operator = uBinaryExpression.getOperator();
    if (operator != UastBinaryOperator.IDENTITY_EQUALS &&
        operator != UastBinaryOperator.IDENTITY_NOT_EQUALS) {
      return;
    }

    final UExpression lhs = uBinaryExpression.getLeftOperand();
    final UExpression rhs = uBinaryExpression.getRightOperand();
    if (lhs instanceof ULiteralExpression || rhs instanceof ULiteralExpression ||
        lhs instanceof UThisExpression || rhs instanceof UThisExpression) {
      return;
    }

    if (isPrimitivePsiTypeReference(lhs) || isPrimitivePsiTypeReference(rhs)) {
      final PsiElement sourcePsi = uBinaryExpression.getSourcePsi();
      if (sourcePsi == null) return;
      holder.registerProblem(sourcePsi, "Primitive types should be compared by equals()");
    }
  }

  private static boolean isPrimitivePsiTypeReference(UExpression uExpression) {
    if (!(uExpression instanceof UReferenceExpression)) {
      return false;
    }
    UReferenceExpression uReferenceExpression = (UReferenceExpression)uExpression;
    final PsiElement resolve = uReferenceExpression.resolve();

    if (resolve instanceof PsiField) {
      final PsiField psiField = (PsiField)resolve;
      if (!psiField.hasModifierProperty(PsiModifier.STATIC) ||
          !psiField.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }

      final PsiClass containingClass = psiField.getContainingClass();
      return containingClass != null &&
             PsiType.class.getName().equals(containingClass.getQualifiedName()) &&
             !"NULL".equals(psiField.getName());
    }
    return false;
  }
}
