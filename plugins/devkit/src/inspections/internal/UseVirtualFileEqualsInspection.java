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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

/**
 * @author peter
 */
public class UseVirtualFileEqualsInspection extends DevKitInspectionBase {
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        if (!ComparisonUtils.isEqualityComparison(expression)) {
          return;
        }
        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        if (rhs == null ||
            lhs.textMatches(PsiKeyword.NULL) || rhs.textMatches(PsiKeyword.NULL) ||
            lhs.textMatches(PsiKeyword.THIS) || rhs.textMatches(PsiKeyword.THIS)) {
          return;
        }
        
        if (InheritanceUtil.isInheritor(lhs.getType(), VirtualFile.class.getName()) || InheritanceUtil.isInheritor(rhs.getType(), VirtualFile.class.getName())) {
          holder.registerProblem(expression, "VirtualFile objects should be compared by equals(), not ==", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

        }
      }
    };
  }
}
