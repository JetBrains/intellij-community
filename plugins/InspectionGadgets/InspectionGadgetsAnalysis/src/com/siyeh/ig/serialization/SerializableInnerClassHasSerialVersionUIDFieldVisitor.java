/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

class SerializableInnerClassHasSerialVersionUIDFieldVisitor
  extends BaseInspectionVisitor {

  private final SerializableInspectionBase inspection;

  public SerializableInnerClassHasSerialVersionUIDFieldVisitor(
    SerializableInspectionBase inspection) {
    this.inspection = inspection;
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    // no call to super, so it doesn't drill down
    if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
      return;
    }
    if (aClass instanceof PsiTypeParameter) {
      return;
    }
    if (inspection.ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
      return;
    }
    if (aClass.findFieldByName(HardcodedMethodConstants.SERIAL_VERSION_UID, false) != null) {
      return;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    if (containingClass == null) {
      return;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    final PsiModifierListOwner staticElement = PsiUtil.getEnclosingStaticElement(aClass, containingClass);
    if (staticElement != null) {
      return;
    }
    if (!SerializationUtils.isSerializable(aClass)) {
      return;
    }
    if (inspection.isIgnoredSubclass(aClass)) {
      return;
    }
    registerClassError(aClass);
  }
}
