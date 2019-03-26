/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

import java.util.Set;

public class FileEqualsUsageInspection extends DevKitInspectionBase {

  static final String MESSAGE =
    "Do not use File.equals/hashCode/compareTo as they don't honor case-sensitivity on macOS. " +
    "Please use FileUtil.filesEquals/fileHashCode/compareFiles instead";

  private static final Set<String> METHOD_NAMES = ContainerUtil.immutableSet("equals", "compareTo", "hashCode");

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiElement resolved = methodExpression.resolve();
        if (!(resolved instanceof PsiMethod)) return;

        PsiMethod method = (PsiMethod)resolved;

        PsiClass clazz = method.getContainingClass();
        if (clazz == null) return;

        if (CommonClassNames.JAVA_IO_FILE.equals(clazz.getQualifiedName()) &&
            METHOD_NAMES.contains(method.getName())) {
          if (JavaPsiFacade.getInstance(holder.getProject()).findClass(FileUtil.class.getName(),
                                                                       expression.getResolveScope()) == null) {
            return;
          }
          
          holder.registerProblem(methodExpression, MESSAGE, ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    };
  }
}
