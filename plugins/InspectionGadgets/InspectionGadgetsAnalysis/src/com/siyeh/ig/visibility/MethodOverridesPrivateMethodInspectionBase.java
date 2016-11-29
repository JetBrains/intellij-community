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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesPrivateMethodInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "MethodOverridesPrivateMethodOfSuperclass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.overrides.private.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.overrides.private.display.name.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverridesPrivateMethodVisitor();
  }

  private static class MethodOverridesPrivateMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (JavaHighlightUtil.isSerializationRelatedMethod(method, aClass)) {
        return;
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        final PsiMethod overridingMethod =
          ancestorClass.findMethodBySignature(method, false);
        if (overridingMethod != null && overridingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
          registerMethodError(method);
          return;
        }
        ancestorClass = ancestorClass.getSuperClass();
      }
    }
  }
}