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

import java.util.Optional;

import static com.intellij.psi.CommonClassNames.JAVA_IO_SERIAL;
import static com.intellij.psi.CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME;
import static com.intellij.psi.PsiModifier.*;

public class MissingSerialAnnotationInspection extends BaseInspection {

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel14OrHigher(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("missing.serial.annotation.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialAnnotationVisitor();
  }

  private static class SerialAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (field.hasAnnotation(JAVA_IO_SERIAL) || !isConstant(field)) return;

      Optional<PsiClass> pClass = getSerializablePsiClass(field);
      if (!pClass.isPresent()) return;

      boolean candidateToBeAnnotated =
        SerializationUtils.isExternalizable(pClass.get()) ? isSerialFieldInExternalizable(field) : isSerialFieldInSerializable(field);
      if (candidateToBeAnnotated) {
        registerError(field.getNameIdentifier(), field);
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.hasAnnotation(JAVA_IO_SERIAL)) return;

      Optional<PsiClass> pClass = getSerializablePsiClass(method);
      if (!pClass.isPresent()) return;

      boolean candidateToBeAnnotated =
        SerializationUtils.isExternalizable(pClass.get()) ? isSerialMethodInExternalizable(method) : isSerialMethodInSerializable(method);
      if (candidateToBeAnnotated) {
        PsiIdentifier methodIdentifier = method.getNameIdentifier();
        if (methodIdentifier == null) return;
        registerError(methodIdentifier, method);
      }
    }

    private static Optional<PsiClass> getSerializablePsiClass(@NotNull PsiElement psiElement) {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      if (psiClass == null) return Optional.empty();
      return !psiClass.isEnum() && SerializationUtils.isSerializable(psiClass) ? Optional.of(psiClass) : Optional.empty();
    }
  }

  static boolean isConstant(@NotNull PsiField field) {
    return field.hasModifierProperty(PRIVATE) && field.hasModifierProperty(STATIC) && field.hasModifierProperty(FINAL);
  }

  static boolean isSerialFieldInSerializable(@NotNull PsiField field) {
    return isSerialFieldInExternalizable(field) ||
           (field.getName().equals("serialPersistentFields") && field.getType().equalsToText("java.io.ObjectStreamField[]"));
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

  static boolean isSerialFieldInExternalizable(@NotNull PsiField field) {
    return field.getName().equals(SERIAL_VERSION_UID_FIELD_NAME) && field.getType().equals(PsiType.LONG);
  }

  static boolean isSerialMethodInExternalizable(@NotNull PsiMethod method) {
    return SerializationUtils.isWriteReplace(method) || SerializationUtils.isReadResolve(method);
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new DelegatingFix(new AddAnnotationFix(JAVA_IO_SERIAL, (PsiModifierListOwner)infos[0], PsiNameValuePair.EMPTY_ARRAY));
  }
}
