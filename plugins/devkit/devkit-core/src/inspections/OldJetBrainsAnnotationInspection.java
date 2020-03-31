// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class OldJetBrainsAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement nameElement = annotation.getNameReferenceElement();
        if (nameElement == null) return;
        String name = nameElement.getText();
        if (!name.equals("Nullable") && !name.equals("NotNull")) return;
        PsiClass annotationClass = ObjectUtils.tryCast(nameElement.resolve(), PsiClass.class);
        if (annotationClass == null) return;
        PsiJavaFile file = ObjectUtils.tryCast(annotationClass.getContainingFile(), PsiJavaFile.class);
        if (file == null) return;
        String packageName = file.getPackageName();
        if (!"org.jetbrains.annotations".equals(packageName)) return;
        Set<PsiAnnotation.TargetType> targets = AnnotationTargetUtil.getAnnotationTargets(annotationClass);
        if (targets == null || targets.contains(PsiAnnotation.TargetType.TYPE_USE)) return;
        PsiAnnotationOwner owner = annotation.getOwner();
        PsiTypeElement typeElement = findTypeElement(owner);
        if (typeElement == null) return;
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiArrayType)) return;
        holder.registerProblem(annotation, "Old-style array annotation", new OldJetBrainsAnnotationFix());
      }
    };
  }

  @Contract("null -> null")
  @Nullable
  private static PsiTypeElement findTypeElement(PsiAnnotationOwner owner) {
    if (!(owner instanceof PsiModifierList)) return null;
    PsiElement parent = ((PsiModifierList)owner).getParent();
    PsiTypeElement typeElement;
    if (parent instanceof PsiVariable) {
      typeElement = ((PsiVariable)parent).getTypeElement();
    }
    else if (parent instanceof PsiMethod) {
      typeElement = ((PsiMethod)parent).getReturnTypeElement();
    }
    else {
      return null;
    }
    if (typeElement == null) return null;
    return typeElement;
  }

  private static class OldJetBrainsAnnotationFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Convert to new-style annotation";
    }
    
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiAnnotation annotation = ObjectUtils.tryCast(descriptor.getStartElement(), PsiAnnotation.class);
      if (annotation == null) return;
      PsiAnnotationOwner owner = annotation.getOwner();
      PsiTypeElement typeElement = findTypeElement(owner);
      if (typeElement == null) return;
      PsiAnnotation copy = (PsiAnnotation)annotation.copy();
      PsiType newType = typeElement.getType().annotate(new TypeAnnotationProvider() {
        @Override
        public PsiAnnotation @NotNull [] getAnnotations() {
          return new PsiAnnotation[]{copy};
        }
      });
      PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(project).createTypeElementFromText(newType.getCanonicalText(true), typeElement);
      typeElement.replace(newTypeElement);
      annotation.delete();
    }
  }
}
