// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Set;
import java.util.function.Supplier;

final class FileEqualsUsageInspection extends DevKitUastInspectionBase {

  private static final Set<String> METHOD_NAMES = Set.of("equals", "compareTo", "hashCode");

  private static final Set<UastBinaryOperator> SUPPORTED_OPERATORS = Set.of(
    UastBinaryOperator.EQUALS,
    UastBinaryOperator.NOT_EQUALS,
    UastBinaryOperator.GREATER,
    UastBinaryOperator.GREATER_OR_EQUALS,
    UastBinaryOperator.LESS,
    UastBinaryOperator.LESS_OR_EQUALS
  );

  @SuppressWarnings("unchecked")
  private static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class, UBinaryExpression.class};

  @Override
  public @NotNull PsiElementVisitor buildInternalVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        inspectCallExpression(node, holder);
        return true;
      }

      @Override
      public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
        inspectBinaryExpression(node, holder);
        return true;
      }
    }, HINTS);
  }

  private static void inspectCallExpression(@NotNull UCallExpression node, @NotNull ProblemsHolder holder) {
    if (!node.isMethodNameOneOf(METHOD_NAMES)) return;
    PsiMethod psiMethod = node.resolve();
    if (psiMethod == null) return;
    inspectMethodCall(psiMethod, holder, () -> {
      UIdentifier identifier = node.getMethodIdentifier();
      if (identifier == null) return null;
      return identifier.getSourcePsi();
    });
  }

  private static void inspectMethodCall(@NotNull PsiMethod psiMethod, @NotNull ProblemsHolder holder,
                                        @NotNull Supplier<PsiElement> anchorSupplier) {
    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return;
    if (!CommonClassNames.JAVA_IO_FILE.equals(containingClass.getQualifiedName())) return;

    if (!DevKitInspectionUtil.isClassAvailable(holder, FileUtil.class.getName())) return;

    PsiElement anchor = anchorSupplier.get();
    if (anchor == null) return;
    holder.registerProblem(anchor, DevKitBundle.message("inspections.file.equals.method"));
  }

  private static void inspectBinaryExpression(@NotNull UBinaryExpression node, @NotNull ProblemsHolder holder) {
    if (!SUPPORTED_OPERATORS.contains(node.getOperator())) return;
    if (isNull(node.getLeftOperand()) || isNull(node.getRightOperand())) return;
    PsiMethod psiMethod = node.resolveOperator();
    if (psiMethod == null) return;
    if (!METHOD_NAMES.contains(psiMethod.getName())) return;
    inspectMethodCall(psiMethod, holder, () -> {
      UIdentifier identifier = node.getOperatorIdentifier();
      return identifier != null ? identifier.getSourcePsi() : null;
    });
  }

  private static boolean isNull(UExpression expression) {
    if (expression instanceof ULiteralExpression literalExpression) {
      return literalExpression.isNull();
    }
    return false;
  }
}
