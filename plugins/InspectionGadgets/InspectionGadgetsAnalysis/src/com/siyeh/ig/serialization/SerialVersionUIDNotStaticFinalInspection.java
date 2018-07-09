/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class SerialVersionUIDNotStaticFinalInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SerialVersionUIDWithWrongSignature";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "serialversionuid.private.static.final.long.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "serialversionuid.private.static.final.long.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return null;
    }
    return new SerialVersionUIDNotStaticFinalFix();
  }

  private static class SerialVersionUIDNotStaticFinalFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "serialversionuid.private.static.final.long.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)parent;
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialVersionUIDNotStaticFinalVisitor();
  }

  private static class SerialVersionUIDNotStaticFinalVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiField field =
        aClass.findFieldByName(
          HardcodedMethodConstants.SERIAL_VERSION_UID, false);
      if (field == null) {
        return;
      }
      final PsiType type = field.getType();
      final boolean wrongType = !PsiType.LONG.equals(type);
      if (field.hasModifierProperty(PsiModifier.STATIC) &&
          field.hasModifierProperty(PsiModifier.PRIVATE) &&
          field.hasModifierProperty(PsiModifier.FINAL) &&
          !wrongType) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerFieldError(field, Boolean.valueOf(wrongType));
    }
  }
}