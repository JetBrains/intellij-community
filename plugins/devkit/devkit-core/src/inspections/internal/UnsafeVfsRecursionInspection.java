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
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodRef = expression.getMethodExpression();
        if (!GET_CHILDREN_METHOD_NAME.equals(methodRef.getReferenceName())) return;

        PsiElement methodElement = methodRef.resolve();
        if (!(methodElement instanceof PsiMethod)) return;
        PsiMethod method = (PsiMethod)methodElement;

        Project project = holder.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

        PsiClass aClass = method.getContainingClass();
        PsiClass virtualFileClass = facade.findClass(VIRTUAL_FILE_CLASS_NAME, GlobalSearchScope.allScope(project));
        if (!InheritanceUtil.isInheritorOrSelf(aClass, virtualFileClass, true)) return;

        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (containingMethod == null) return;

        String containingMethodName = containingMethod.getName();
        Ref<Boolean> result = Ref.create();
        containingMethod.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression2) {
            super.visitMethodCallExpression(expression2);
            if (expression2 != expression &&
                containingMethodName.equals(expression2.getMethodExpression().getReferenceName()) &&
                expression2.resolveMethod() == containingMethod) {
              result.set(Boolean.TRUE);
            }
          }
        });
        if (!result.isNull()) {
          holder.registerProblem(expression, DevKitBundle.message("inspections.unsafe.vfs.recursion"));
        }
      }
    };
  }
}