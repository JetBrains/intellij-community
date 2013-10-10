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
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

class SerializableInnerClassWithNonSerializableOuterClassVisitor
  extends BaseInspectionVisitor {

  private final SerializableInspectionBase inspection;

  public SerializableInnerClassWithNonSerializableOuterClassVisitor(
    SerializableInspectionBase inspection) {
    this.inspection = inspection;
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    if (aClass.isInterface() || aClass.isAnnotationType() ||
        aClass.isEnum()) {
      return;
    }
    if (inspection.ignoreAnonymousInnerClasses &&
        aClass instanceof PsiAnonymousClass) {
      return;
    }
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass == null) {
      return;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (!SerializationUtils.isSerializable(aClass)) {
      return;
    }
    if (SerializationUtils.isSerializable(containingClass)) {
      return;
    }
    if (inspection.isIgnoredSubclass(aClass)) {
      return;
    }
    registerClassError(aClass);
  }
}
