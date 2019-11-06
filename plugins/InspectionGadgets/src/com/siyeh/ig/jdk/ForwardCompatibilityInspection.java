// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ForwardCompatibilityInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(holder.getFile());
    JavaSdkVersion sdkVersion = JavaVersionService.getInstance().getJavaSdkVersion(holder.getFile());
    return new JavaElementVisitor() {
      @Override
      public void visitIdentifier(PsiIdentifier identifier) {
        String name = identifier.getText();
        if ("_".equals(name) && sdkVersion != null &&
            sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) && languageLevel.isLessThan(LanguageLevel.JDK_1_9)) {
          String message = JavaErrorMessages.message("underscore.identifier.warn");
          holder.registerProblem(identifier, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
        PsiElement parent = identifier.getParent();
        if (PsiKeyword.VAR.equals(name) && parent instanceof PsiClass &&
            languageLevel.isLessThan(LanguageLevel.JDK_10)) {
          String message = JavaErrorMessages.message("var.identifier.warn");
          holder.registerProblem(identifier, message, new RenameFix());
        }
        if (PsiKeyword.ASSERT.equals(name) && languageLevel.isLessThan(LanguageLevel.JDK_1_4) && 
            (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
          String message = JavaErrorMessages.message("assert.identifier.warn");
          holder.registerProblem(identifier, message, new RenameFix());
        }
        if (PsiKeyword.ENUM.equals(name) && languageLevel.isLessThan(LanguageLevel.JDK_1_5) &&
            (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
          String message = JavaErrorMessages.message("enum.identifier.warn");
          holder.registerProblem(identifier, message, new RenameFix());
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression ref = expression.getMethodExpression();
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null && PsiKeyword.YIELD.equals(nameElement.getText()) && ref.getQualifierExpression() == null) {
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
