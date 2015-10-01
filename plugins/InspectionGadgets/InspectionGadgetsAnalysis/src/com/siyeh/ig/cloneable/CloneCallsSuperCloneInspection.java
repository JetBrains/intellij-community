/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.cloneable;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class CloneCallsSuperCloneInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "CloneDoesntCallSuperClone";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "clone.doesnt.call.super.clone.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "clone.doesnt.call.super.clone.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NoExplicitCloneCallsVisitor();
  }

  private static class NoExplicitCloneCallsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      if (!CloneUtils.isClone(method)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isInterface() ||
          containingClass.isAnnotationType()) {
        return;
      }
      if (CloneUtils.onlyThrowsException(method)) {
        return;
      }
      if (MethodCallUtils.containsSuperMethodCall(HardcodedMethodConstants.CLONE, method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}