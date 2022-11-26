// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public class UnsafeVfsRecursionInspection extends DevKitUastInspectionBase {

  private static final String VIRTUAL_FILE_CLASS_NAME = VirtualFile.class.getName();
  private static final String GET_CHILDREN_METHOD_NAME = "getChildren";
  private static final String GET_CHILDREN_PROPERTY_ACCESS_NAME = "children"; // for language with property access syntax
  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class, UQualifiedReferenceExpression.class};

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        inspectExpression(node, holder);
        return true;
      }

      @Override
      public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
        inspectExpression(node, holder);
        return true;
      }
    }, HINTS);
  }

  private static void inspectExpression(@NotNull UExpression expression, @NotNull ProblemsHolder holder) {
    if (isVirtualFileGetChildrenMethodCall(expression) && isCalledInRecursiveMethod(expression)) {
      PsiElement sourcePsi = expression.getSourcePsi();
      if (sourcePsi != null) {
        holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.unsafe.vfs.recursion"));
      }
    }
  }

  private static boolean isVirtualFileGetChildrenMethodCall(@NotNull UExpression expression) {
    PsiMethod getChildrenMethod = tryToResolveGetVisitChildrenMethod(expression);
    if (getChildrenMethod == null) return false;
    return InheritanceUtil.isInheritor(getChildrenMethod.getContainingClass(), VIRTUAL_FILE_CLASS_NAME);
  }

  @Nullable
  private static PsiMethod tryToResolveGetVisitChildrenMethod(@NotNull UExpression expression) {
    if (expression instanceof UCallExpression methodCall && UastExpressionUtils.isMethodCall(methodCall)) {
      if (GET_CHILDREN_METHOD_NAME.equals(methodCall.getMethodName())) {
        return methodCall.resolve();
      }
    }
    else if (expression instanceof UQualifiedReferenceExpression qualifiedReference) {
      PsiElement selectorPsi = qualifiedReference.getSelector().getSourcePsi();
      if (selectorPsi == null) return null;
      if (GET_CHILDREN_PROPERTY_ACCESS_NAME.equals(selectorPsi.getText())) {
        PsiElement resolveResult = qualifiedReference.resolve();
        return resolveResult instanceof PsiMethod ? (PsiMethod)resolveResult : null;
      }
    }
    return null;
  }

  private static boolean isCalledInRecursiveMethod(@NotNull UExpression getChildrenMethodCall) {
    UMethod containingMethod = UastUtils.getParentOfType(getChildrenMethodCall, UMethod.class);
    if (containingMethod == null) return false;
    String containingMethodName = containingMethod.getName();

    Ref<Boolean> isInRecursiveCall = Ref.create();
    PsiElement methodSourcePsi = containingMethod.getSourcePsi();
    if (methodSourcePsi == null) return false;
    methodSourcePsi.accept(new PsiRecursiveElementVisitor() {

      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        UCallExpression potentialRecursiveCall = UastContextKt.toUElement(element, UCallExpression.class);
        if (potentialRecursiveCall == null) return;
        if (!UastExpressionUtils.isMethodCall(potentialRecursiveCall)) return;
        if (potentialRecursiveCall != getChildrenMethodCall &&
            containingMethodName.equals(potentialRecursiveCall.getMethodName()) &&
            expressionResolvesToMethod(potentialRecursiveCall, containingMethod)) {
          isInRecursiveCall.set(Boolean.TRUE);
        }
      }

      private static boolean expressionResolvesToMethod(@NotNull UCallExpression potentialRecursiveCall, @NotNull UMethod uMethod) {
        PsiMethod resolvedMethod = potentialRecursiveCall.resolve();
        if (resolvedMethod == null) return false;
        UElement resolvedUMethod = UastContextKt.toUElement(resolvedMethod);
        if (resolvedUMethod == null) return false;
        return uMethod.getSourcePsi() == resolvedUMethod.getSourcePsi();
      }
    });
    return Boolean.TRUE == isInRecursiveCall.get();
  }
}
