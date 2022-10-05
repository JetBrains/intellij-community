// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class UnsafeVfsRecursionInspection extends DevKitUastInspectionBase {

  private static final String VIRTUAL_FILE_CLASS_NAME = VirtualFile.class.getName();
  private static final String GET_CHILDREN_METHOD_NAME = "getChildren";
  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class};
  public static final boolean DO_NOT_VISIT_CHILDREN = true;

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        inspectCallExpression(node, holder);
        return true;
      }
    }, HINTS);
  }

  private static void inspectCallExpression(@NotNull UCallExpression expression, @NotNull ProblemsHolder holder) {
    if (isVirtualFileGetChildrenMethodCall(expression, holder.getProject()) && isCalledInRecursiveMethod(expression)) {
      PsiElement sourcePsi = expression.getSourcePsi();
      if (sourcePsi != null) {
        holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.unsafe.vfs.recursion"));
      }
    }
  }

  private static boolean isVirtualFileGetChildrenMethodCall(@NotNull UCallExpression expression, @NotNull Project project) {
    if (!UastExpressionUtils.isMethodCall(expression)) return false;
    String expressionMethodName = expression.getMethodName();
    if (!GET_CHILDREN_METHOD_NAME.equals(expressionMethodName)) return false;

    PsiMethod getChildrenMethod = expression.resolve();
    if (getChildrenMethod == null) return false;

    PsiClass aClass = getChildrenMethod.getContainingClass();
    PsiClass virtualFileClass = JavaPsiFacade.getInstance(project).findClass(VIRTUAL_FILE_CLASS_NAME, GlobalSearchScope.allScope(project));
    return InheritanceUtil.isInheritorOrSelf(aClass, virtualFileClass, true);
  }

  private static boolean isCalledInRecursiveMethod(@NotNull UCallExpression getChildrenMethodCall) {
    UMethod containingMethod = UastUtils.getParentOfType(getChildrenMethodCall, UMethod.class);
    if (containingMethod == null) return false;
    String containingMethodName = containingMethod.getName();

    Ref<Boolean> isInRecursiveCall = Ref.create();
    containingMethod.accept(new AbstractUastVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression potentialRecursiveCall) {
        if (!UastExpressionUtils.isMethodCall(potentialRecursiveCall)) return DO_NOT_VISIT_CHILDREN;
        if (potentialRecursiveCall != getChildrenMethodCall &&
            containingMethodName.equals(potentialRecursiveCall.getMethodName()) &&
            expressionResolvesToMethod(potentialRecursiveCall, containingMethod)) {
          isInRecursiveCall.set(Boolean.TRUE);
        }
        return DO_NOT_VISIT_CHILDREN;
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
