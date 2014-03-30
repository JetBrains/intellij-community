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

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class SerializableHasSerializationMethodsInspectionBase
  extends SerializableInspectionBase {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "serializable.has.serialization.methods.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean hasReadObject = ((Boolean)infos[0]).booleanValue();
    final boolean hasWriteObject = ((Boolean)infos[1]).booleanValue();
    if (!hasReadObject && !hasWriteObject) {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor");
    }
    else if (hasReadObject) {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor2");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableHasSerializationMethodsVisitor();
  }

  private class SerializableHasSerializationMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType() ||
          aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      if (ignoreAnonymousInnerClasses &&
          aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      final boolean hasReadObject =
        SerializationUtils.hasReadObject(aClass);
      final boolean hasWriteObject =
        SerializationUtils.hasWriteObject(aClass);
      if (hasWriteObject && hasReadObject) {
        return;
      }
      if (isIgnoredSubclass(aClass)) {
        return;
      }
      registerClassError(aClass, Boolean.valueOf(hasReadObject),
                         Boolean.valueOf(hasWriteObject));
    }
  }
}