/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

public class UnsafeVfsRecursionInspection extends InternalInspection {
  private static final String VIRTUAL_FILE_CLASS_NAME = VirtualFile.class.getName();
  private static final String GET_CHILDREN_METHOD_NAME = "getChildren";

  private static final String MESSAGE =
    "VirtualFile.getChildren() is called from a recursive method. " +
    "This may cause an endless loop on cyclic symlinks. " +
    "Please use VfsUtilCore.visitChildrenRecursively() instead.";

  @NotNull
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(final PsiMethodCallExpression expression) {
        final Project project = expression.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

        final PsiReferenceExpression methodRef = expression.getMethodExpression();
        if (!GET_CHILDREN_METHOD_NAME.equals(methodRef.getReferenceName())) return;
        final PsiElement methodElement = methodRef.resolve();
        if (!(methodElement instanceof PsiMethod)) return;
        final PsiMethod method = (PsiMethod)methodElement;
        final PsiClass aClass = method.getContainingClass();
        final PsiClass virtualFileClass = facade.findClass(VIRTUAL_FILE_CLASS_NAME, GlobalSearchScope.allScope(project));
        if (!InheritanceUtil.isInheritorOrSelf(aClass, virtualFileClass, true)) return;

        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (containingMethod == null) return;
        final String containingMethodName = containingMethod.getName();
        final Ref<Boolean> result = Ref.create();
        containingMethod.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitMethodCallExpression(final PsiMethodCallExpression expression2) {
            if (expression2 != expression &&
                containingMethodName.equals(expression2.getMethodExpression().getReferenceName()) &&
                expression2.resolveMethod() == containingMethod) {
              result.set(Boolean.TRUE);
            }
          }
        });

        if (!result.isNull()) {
          holder.registerProblem(expression, MESSAGE);
        }
      }
    };
  }
}
