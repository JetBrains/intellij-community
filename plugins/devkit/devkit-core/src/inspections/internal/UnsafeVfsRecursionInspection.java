// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

public class UnsafeVfsRecursionInspection extends DevKitInspectionBase {

  private static final String VIRTUAL_FILE_CLASS_NAME = VirtualFile.class.getName();
  private static final String GET_CHILDREN_METHOD_NAME = "getChildren";

  @NotNull
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        if (isVirtualFileGetChildrenMethodCall(expression, holder.getProject()) && isCalledInRecursiveMethod(expression)) {
          holder.registerProblem(expression, DevKitBundle.message("inspections.unsafe.vfs.recursion"));
        }
      }
    };
  }

  private static boolean isVirtualFileGetChildrenMethodCall(@NotNull PsiMethodCallExpression expression, @NotNull Project project) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (!GET_CHILDREN_METHOD_NAME.equals(methodExpression.getReferenceName())) return false;

    PsiElement methodElement = methodExpression.resolve();
    if (!(methodElement instanceof PsiMethod)) return false;
    PsiMethod getChildrenMethod = (PsiMethod)methodElement;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    PsiClass aClass = getChildrenMethod.getContainingClass();
    PsiClass virtualFileClass = facade.findClass(VIRTUAL_FILE_CLASS_NAME, GlobalSearchScope.allScope(project));

    return InheritanceUtil.isInheritorOrSelf(aClass, virtualFileClass, true);
  }

  private static boolean isCalledInRecursiveMethod(@NotNull PsiMethodCallExpression expression) {
    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (containingMethod == null) return false;

    String containingMethodName = containingMethod.getName();
    Ref<Boolean> result = Ref.create();
    containingMethod.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression2) {
        super.visitMethodCallExpression(expression2);
        if (expression2 != expression &&
            containingMethodName.equals(expression2.getMethodExpression().getReferenceName()) &&
            expression2.resolveMethod() == containingMethod) {
          result.set(Boolean.TRUE);
        }
      }
    });
    return !result.isNull();
  }
}
