// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerialAnnotationUsedOnWrongMemberInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "serial";
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel14OrHigher(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("serial.annotation.used.on.wrong.member.problem.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialAnnotationVisitor();
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new DelegatingFix(new RemoveAnnotationQuickFix((PsiAnnotation)infos[0], null));
  }

  private static class SerialAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!CommonClassNames.JAVA_IO_SERIAL.equals(annotation.getQualifiedName())) return;

      PsiClass psiClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (psiClass == null) return;
      if (!SerializationUtils.isSerializable(psiClass)) {
        registerError(annotation, annotation);
        return;
      }

      PsiAnnotationOwner annotationOwner = annotation.getOwner();
      if (!(annotationOwner instanceof PsiModifierList)) return;
      PsiElement annotationOwnerParent = ((PsiModifierList)annotationOwner).getParent();
      PsiField psiField = null;
      PsiMethod psiMethod = null;
      if (annotationOwnerParent instanceof PsiField) {
        psiField = (PsiField)annotationOwnerParent;
      }
      else if (annotationOwnerParent instanceof PsiMethod) {
        psiMethod = (PsiMethod)annotationOwnerParent;
      }
      else {
        return;
      }

      boolean isWellAnnotatedElement;
      if (SerializationUtils.isExternalizable(psiClass)) {
        isWellAnnotatedElement = psiField == null ? MissingSerialAnnotationInspection.isSerialMethodInExternalizable(psiMethod)
                                                  : MissingSerialAnnotationInspection.isConstant(psiField) &&
                                                    MissingSerialAnnotationInspection.isSerialFieldInExternalizable(psiField);
      }
      else {
        isWellAnnotatedElement = psiField == null ? MissingSerialAnnotationInspection.isSerialMethodInSerializable(psiMethod)
                                                  : MissingSerialAnnotationInspection.isConstant(psiField) &&
                                                    MissingSerialAnnotationInspection.isSerialFieldInSerializable(psiField);
      }
      if (!isWellAnnotatedElement) {
        registerError(annotation, annotation);
      }
    }
  }
}