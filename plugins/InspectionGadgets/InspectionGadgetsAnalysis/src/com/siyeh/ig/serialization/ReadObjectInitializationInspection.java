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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class ReadObjectInitializationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "InstanceVariableMayNotBeInitializedByReadObject";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "readobject.initialization.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "readobject.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReadObjectInitializationVisitor();
  }

  private static class ReadObjectInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // no call to super, so it doesn't drill down
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      if (!SerializationUtils.isReadObject(method)) {
        return;
      }
      final boolean defaultReadObjectCalled =
        ControlFlowUtils.elementContainsCallToMethod(method, "java.io.ObjectInputStream",
                                                     PsiType.VOID, "defaultReadObject");
      final PsiField[] fields = aClass.getFields();
      if (defaultReadObjectCalled) {
        for (final PsiField field : fields) {
          if (field.hasModifierProperty(PsiModifier.TRANSIENT) &&
              !isFieldInitialized(field, method)) {
            registerFieldError(field);
          }
        }
      }
      else {
        for (final PsiField field : fields) {
          if (!isFieldInitialized(field, method)) {
            registerFieldError(field);
          }
        }
      }
    }

    public static boolean isFieldInitialized(@NotNull PsiField field,
                                             @NotNull PsiMethod method) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL) &&
          field.getInitializer() != null) {
        return true;
      }
      return InitializationUtils.methodAssignsVariableOrFails(method,
                                                              field);
    }
  }
}