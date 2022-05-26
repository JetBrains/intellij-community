// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class RedundantExplicitVariableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_10)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (!typeElement.isInferredType()) {
          PsiElement parent = variable.getParent();
          if (parent instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)parent).getDeclaredElements().length > 1) {
            return;
          }
          doCheck(variable, (PsiLocalVariable)variable.copy(), typeElement);
        }
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        PsiParameter parameter = statement.getIterationParameter();
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null && !typeElement.isInferredType()) {
          PsiForeachStatement copy = (PsiForeachStatement)statement.copy();
          doCheck(parameter, copy.getIterationParameter(), typeElement);
        }
      }

      private void doCheck(PsiVariable variable,
                           PsiVariable copyVariable,
                           PsiTypeElement element2Highlight) {
        ArrayList<PsiAnnotation> typeUseAnnotations = new ArrayList<>();
        AnnotationTargetUtil.collectStrictlyTypeUseAnnotations(copyVariable.getModifierList(), typeUseAnnotations);
        if (!typeUseAnnotations.isEmpty()) return;

        PsiTypeElement typeElementCopy = copyVariable.getTypeElement();
        if (typeElementCopy != null) {
          IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar(typeElementCopy, variable);
          if (variable.getType().equals(getNormalizedType(copyVariable))) {
            holder.registerProblem(element2Highlight,
                                   InspectionGadgetsBundle.message("inspection.redundant.explicit.variable.type.description"),
                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                   new ReplaceWithVarFix());
          }
        }
       }

      private PsiType getNormalizedType(PsiVariable copyVariable) {
        PsiType type = copyVariable.getType();
        PsiClass refClass = PsiUtil.resolveClassInType(type);
        if (refClass instanceof PsiAnonymousClass) {
          type = ((PsiAnonymousClass)refClass).getBaseClassType();
        }
        return type;
      }
    };
  }

  private static class ReplaceWithVarFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.var.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiTypeElement) {
        CodeStyleManager.getInstance(project)
          .reformat(IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar((PsiTypeElement)element, element));
      }
    }
  }
}
