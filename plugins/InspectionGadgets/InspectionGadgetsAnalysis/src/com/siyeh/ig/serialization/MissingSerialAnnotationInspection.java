// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.intention.AddAnnotationFix;
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

import static com.intellij.psi.CommonClassNames.JAVA_IO_SERIAL;
import static com.intellij.psi.PsiModifier.PRIVATE;

public class MissingSerialAnnotationInspection extends BaseInspection {

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel14OrHigher(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    final Object member = infos[0];
    if (member instanceof PsiField) {
      return InspectionGadgetsBundle.message("missing.serial.annotation.on.field.problem.descriptor", member);
    }
    else {
      return InspectionGadgetsBundle.message("missing.serial.annotation.on.method.problem.descriptor", member);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialAnnotationVisitor();
  }

  private static class SerialAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasAnnotation(JAVA_IO_SERIAL)) return;

      PsiClass pClass = getSerializablePsiClass(field);
      if (pClass == null) return;

      boolean candidateToBeAnnotated;
      if (pClass.isRecord()) {
        candidateToBeAnnotated = SerializationUtils.isSerialVersionUid(field);
      }
      else {
        candidateToBeAnnotated = SerializationUtils.isExternalizable(pClass) ? SerializationUtils.isSerialVersionUid(field)
                                                                             : SerializationUtils.isSerialVersionUid(field) ||
                                                                               SerializationUtils.isSerialPersistentFields(field);
      }
      if (candidateToBeAnnotated) {
        registerFieldError(field, field);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.hasAnnotation(JAVA_IO_SERIAL)) return;

      PsiClass pClass = getSerializablePsiClass(method);
      if (pClass == null) return;

      boolean candidateToBeAnnotated;
      if (pClass.isRecord()) {
        candidateToBeAnnotated = isSerialMethodInExternalizable(method);
      }
      else {
        candidateToBeAnnotated = SerializationUtils.isExternalizable(pClass) ? isSerialMethodInExternalizable(method)
                                                                             : isSerialMethodInSerializable(method);
      }
      if (candidateToBeAnnotated) {
        registerMethodError(method, method);
      }
    }
  }

  @Nullable
  static PsiClass getSerializablePsiClass(@NotNull PsiElement psiElement) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
    if (psiClass == null) return null;
    return !psiClass.isEnum() && SerializationUtils.isSerializable(psiClass) ? psiClass : null;
  }

  static boolean isSerialMethodInSerializable(@NotNull PsiMethod method) {
    if (method.hasModifierProperty(PRIVATE) &&
        (SerializationUtils.isWriteObject(method) ||
         SerializationUtils.isReadObject(method) ||
         SerializationUtils.isReadObjectNoData(method))) {
      return true;
    }

    return isSerialMethodInExternalizable(method);
  }

  static boolean isSerialMethodInExternalizable(@NotNull PsiMethod method) {
    return SerializationUtils.isWriteReplace(method) || SerializationUtils.isReadResolve(method);
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new DelegatingFix(new AddAnnotationFix(JAVA_IO_SERIAL, (PsiModifierListOwner)infos[0], PsiNameValuePair.EMPTY_ARRAY));
  }
}
