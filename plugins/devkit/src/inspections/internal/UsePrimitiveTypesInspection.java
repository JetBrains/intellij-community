/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UsePrimitiveTypesInspection extends InternalInspection {
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        final IElementType tokenType = expression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE)) {
          final PsiExpression lOperand = expression.getLOperand();
          final PsiExpression rOperand = expression.getROperand();
          if (rOperand != null && (isPrimitiveTypeRef(lOperand) || isPrimitiveTypeRef(rOperand))) {
            final String name;
            if (JavaTokenType.NE.equals(tokenType)) {
              name = IntentionPowerPackBundle.message("replace.equality.with.not.equals.intention.name");
            }
            else {
              name = IntentionPowerPackBundle.message("replace.equality.with.equals.intention.name");
            }
            holder.registerProblem(expression.getOperationSign(),
                                   "Primitive types should be compared with .equals",
                                   new ReplaceEqualityWithEqualsFix(name));
          }
        }
      }
    };
  }
  
  private static boolean isPrimitiveTypeRef(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (target instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)target).getContainingClass();
        return containingClass != null && 
               PsiType.class.getName().equals(containingClass.getQualifiedName()) && 
               !"NULL".equals(((PsiField)target).getName());
      }
    }
    return false;
  }

  private static class ReplaceEqualityWithEqualsFix implements LocalQuickFix {
    private final String myName;

    public ReplaceEqualityWithEqualsFix(String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace equality with .equals";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiJavaToken) {

        final IElementType tokenType = ((PsiJavaToken)psiElement).getTokenType();
        final String prefix;
        if (tokenType.equals(JavaTokenType.EQEQ)) {
          prefix = "";
        }
        else if (tokenType.equals(JavaTokenType.NE)) {
          prefix = "!";
        }
        else {
          return;
        }

        final PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiBinaryExpression) {
          final PsiExpression rOperand = ((PsiBinaryExpression)parent).getROperand();
          final PsiExpression lOperand = ((PsiBinaryExpression)parent).getLOperand();
          if (rOperand != null) {
            final boolean flip = isPrimitiveTypeRef(rOperand);
            if (flip || isPrimitiveTypeRef(lOperand)) {
              final String rText = PsiUtil.skipParenthesizedExprUp(rOperand).getText();
              final String lText = PsiUtil.skipParenthesizedExprUp(lOperand).getText();

              final String lhText = flip ? rText : lText;
              final String rhText = flip ? lText : rText;

              @NonNls final String expString = prefix + lhText + ".equals(" + rhText + ')';
              PsiReplacementUtil.replaceExpression((PsiBinaryExpression)parent, expString);
            }
          }
        }
      }
    }
  }
}
