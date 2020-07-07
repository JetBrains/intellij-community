// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

/**
 * @author peter
 */
public class UseVirtualFileEqualsInspection extends DevKitUastInspectionBase {

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
        inspectBinaryExpression(node, holder);

        return true;
      }
    }, new Class[]{UBinaryExpression.class});
  }

  private static void inspectBinaryExpression(@NotNull UBinaryExpression uBinaryExpression, ProblemsHolder holder) {
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

    if (InheritanceUtil.isInheritor(lhs.getExpressionType(), VirtualFile.class.getName()) ||
        InheritanceUtil.isInheritor(rhs.getExpressionType(), VirtualFile.class.getName())) {
      final PsiElement sourcePsi = uBinaryExpression.getSourcePsi();
      if (sourcePsi == null) return;
      holder.registerProblem(sourcePsi, "VirtualFile objects should be compared by equals(), not ==");
    }
  }
}
