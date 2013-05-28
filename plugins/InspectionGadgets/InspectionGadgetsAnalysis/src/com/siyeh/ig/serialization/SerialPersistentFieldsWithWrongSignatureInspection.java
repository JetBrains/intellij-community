/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SerialPersistentFieldsWithWrongSignatureInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "serialpersistentfields.with.wrong.signature.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "serialpersistentfields.with.wrong.signature.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialPersistentFieldsWithWrongSignatureVisitor();
  }

  private static class SerialPersistentFieldsWithWrongSignatureVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      PsiField badSerialPersistentFields = null;
      final PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (isSerialPersistentFields(field)) {
          if (!field.hasModifierProperty(PsiModifier.PRIVATE) ||
              !field.hasModifierProperty(PsiModifier.STATIC) ||
              !field.hasModifierProperty(PsiModifier.FINAL)) {
            badSerialPersistentFields = field;
            break;
          }
          else {
            final PsiType type = field.getType();
            if (!type.equalsToText("java.io.ObjectStreamField" +
                                   "[]")) {
              badSerialPersistentFields = field;
              break;
            }
          }
        }
      }
      if (badSerialPersistentFields == null) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerFieldError(badSerialPersistentFields);
    }

    private static boolean isSerialPersistentFields(PsiField field) {
      @NonNls final String fieldName = field.getName();
      return "serialPersistentFields".equals(fieldName);
    }
  }
}