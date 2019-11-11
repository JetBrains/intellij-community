// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForwardCompatibilityInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(holder.getFile());
    return new JavaElementVisitor() {
      @Override
      public void visitIdentifier(PsiIdentifier identifier) {
        String message = getIdentifierWarning(identifier);
        if (message != null) {
          holder.registerProblem(identifier, message, new RenameFix());
        }
      }

      @Nullable
      private String getIdentifierWarning(PsiIdentifier identifier) {
        String name = identifier.getText();
        PsiElement parent = identifier.getParent();
        switch (name) {
          case PsiKeyword.ASSERT:
            if (languageLevel.isLessThan(LanguageLevel.JDK_1_4) &&
                (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
              return JavaErrorMessages.message("assert.identifier.warn");
            }
            break;
          case PsiKeyword.ENUM:
            if (languageLevel.isLessThan(LanguageLevel.JDK_1_5) &&
                (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
              return JavaErrorMessages.message("enum.identifier.warn");
            }
            break;
          case "_":
            if (languageLevel.isLessThan(LanguageLevel.JDK_1_9)) {
              return JavaErrorMessages.message("underscore.identifier.warn");
            }
            break;
          case PsiKeyword.VAR:
            if (languageLevel.isLessThan(LanguageLevel.JDK_10) && parent instanceof PsiClass) {
              return JavaErrorMessages.message("var.identifier.warn");
            }
            break;
          case PsiKeyword.YIELD:
            if (languageLevel.isLessThan(LanguageLevel.JDK_X) && parent instanceof PsiClass) {
              return JavaErrorMessages.message("yield.identifier.warn");
            }
            break;
        }
        return null;
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression ref = expression.getMethodExpression();
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null && PsiKeyword.YIELD.equals(nameElement.getText()) && ref.getQualifierExpression() == null &&
            languageLevel.isLessThan(LanguageLevel.JDK_X)) {
          PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(expression.getMethodExpression());
          holder.registerProblem(nameElement, JavaErrorMessages.message("yield.unqualified.method.warn"),
                                   qualifier == null ? null : new QualifyCallFix(), new RenameFix());
        }
      }

      @Override
      public void visitKeyword(PsiKeyword keyword) {
        super.visitKeyword(keyword);
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !languageLevel.isAtLeast(LanguageLevel.JDK_10)) {
          @PsiModifier.ModifierConstant String modifier = keyword.getText();
          if (PsiKeyword.STATIC.equals(modifier) || PsiKeyword.TRANSITIVE.equals(modifier)) {
            PsiElement parent = keyword.getParent();
            if (parent instanceof PsiModifierList) {
              PsiElement grand = parent.getParent();
              if (grand instanceof PsiRequiresStatement && PsiJavaModule.JAVA_BASE.equals(((PsiRequiresStatement)grand).getModuleName())) {
                String message = JavaErrorMessages.message("module.unwanted.modifier.warn");
                LocalQuickFix fix = QuickFixFactory.getInstance().createModifierListFix((PsiModifierList)parent, modifier, false, false);
                holder.registerProblem(keyword, message, fix);
              }
            }
          }
        }
      }
    };
  }

  private static class QualifyCallFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Qualify call";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return;
      call.getMethodExpression().setQualifierExpression(qualifier);
    }
  }
}
