// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.psi.PsiModifier.*;

public class SerialAnnotationCouldBeSuggestedInspection extends BaseInspection {
  public static final String SERIAL_ANNOTATION = "java.io.Serial";

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel14OrHigher(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("serial.annotation.could.be.suggested.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialAnnotationVisitor();
  }

  private static class SerialAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isEnum()) return;
      if (!SerializationUtils.isSerializable(aClass)) return;

      Stream<PsiField> fieldsStream = Arrays.stream(aClass.getFields()).filter(field -> !field.hasAnnotation(SERIAL_ANNOTATION))
        .filter(SerialAnnotationCouldBeSuggestedInspection::isConstant);
      Stream<PsiMethod> methodsStream = Arrays.stream(aClass.getMethods()).filter(method -> !method.hasAnnotation(SERIAL_ANNOTATION));
      if (SerializationUtils.isExternalizable(aClass)) {
        fieldsStream = fieldsStream.filter(SerialAnnotationCouldBeSuggestedInspection::isSerialFieldInExternalizable);
        methodsStream = methodsStream.filter(SerialAnnotationCouldBeSuggestedInspection::isSerialMethodInExternalizable);
      }
      else {
        fieldsStream = fieldsStream.filter(SerialAnnotationCouldBeSuggestedInspection::isSerialFieldInSerializable);
        methodsStream = methodsStream.filter(SerialAnnotationCouldBeSuggestedInspection::isSerialMethodInSerializable);
      }
      fieldsStream.map(PsiField::getNameIdentifier).forEach(field -> registerError(field, "Field"));
      methodsStream.map(PsiMethod::getNameIdentifier).filter(Objects::nonNull).forEach(method -> registerError(method, "Method"));
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
    return field.getName().equals("serialVersionUID") && field.getType().equals(PsiType.LONG);
  }

  static boolean isSerialMethodInExternalizable(@NotNull PsiMethod method) {
    return SerializationUtils.isWriteReplace(method) || SerializationUtils.isReadResolve(method);
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        PsiModifierListOwner psiOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiModifierListOwner.class);
        if (psiOwner == null) return;

        new AddAnnotationFix(SERIAL_ANNOTATION, psiOwner, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("serial.annotation.could.be.suggested.quickfix");
      }
    };
  }
}
