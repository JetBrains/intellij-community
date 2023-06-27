// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

public class RedundantExplicitVariableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_10)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (!typeElement.isInferredType()) {
          PsiElement parent = variable.getParent();
          if (parent instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)parent).getDeclaredElements().length > 1) {
            return;
          }
          PsiExpression initializer = variable.getInitializer();
          if (initializer instanceof PsiFunctionalExpression) {
            return;
          }
          doCheck(variable, (PsiLocalVariable)variable.copy(), typeElement);
        }
      }

      @Override
      public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        PsiParameter parameter = statement.getIterationParameter();
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null && !typeElement.isInferredType()) {
          PsiForeachStatement copy = (PsiForeachStatement)statement.copy();
          doCheck(parameter, Objects.requireNonNull(copy.getIterationParameter()), typeElement);
        }
      }

      @Override
      public void visitPatternVariable(@NotNull PsiPatternVariable variable) {
        PsiPattern deconstructionComponent = variable.getPattern();
        if (deconstructionComponent.getParent() instanceof PsiDeconstructionList deconstructionList &&
            deconstructionList.getParent() instanceof PsiDeconstructionPattern deconstruction) {
          PsiTypeElement typeElement = variable.getTypeElement();
          if (!typeElement.isInferredType()) {
            @NotNull PsiPattern @NotNull [] patterns = deconstructionList.getDeconstructionComponents();
            int index = ArrayUtil.indexOf(patterns, deconstructionComponent);
            // We need a copy of the entire pattern, not just the pattern variable, as we will
            // replace the variable type with 'var' inside the 'doCheck' method by calling the
            // 'IntroduceVariableUtil#expandDiamondsAndReplaceExplicitTypeWithVar' method.
            // Without the record pattern as context, we will not be able to get the type of
            // the deconstruction component variable when calling the 'getNormalizedType' method.
            PsiDeconstructionPattern deconstructionCopy = (PsiDeconstructionPattern)deconstruction.copy();
            PsiPattern componentCopy = deconstructionCopy.getDeconstructionList().getDeconstructionComponents()[index];
            doCheck(variable, Objects.requireNonNull(JavaPsiPatternUtil.getPatternVariable(componentCopy)), typeElement);
          }
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
                                   new ReplaceWithVarFix());
          }
        }
       }

      private static PsiType getNormalizedType(PsiVariable copyVariable) {
        PsiType type = copyVariable.getType();
        PsiClass refClass = PsiUtil.resolveClassInType(type);
        if (refClass instanceof PsiAnonymousClass anonymousClass) {
          type = anonymousClass.getBaseClassType();
        }
        return type;
      }
    };
  }

  private static class ReplaceWithVarFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.var.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiTypeElement typeElement) {
        CodeStyleManager.getInstance(project)
          .reformat(IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar(typeElement, element));
      }
    }
  }
}
