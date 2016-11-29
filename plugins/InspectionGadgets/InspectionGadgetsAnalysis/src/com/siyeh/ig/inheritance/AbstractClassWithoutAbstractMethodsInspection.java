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
package com.siyeh.ig.inheritance;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class AbstractClassWithoutAbstractMethodsInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "abstract.class.without.abstract.methods.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "abstract.class.without.abstract.methods.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractClassWithoutAbstractMethodsVisitor();
  }

  private static class AbstractClassWithoutAbstractMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (hasAbstractMethods(aClass)) {
        return;
      }
      registerClassError(aClass);
    }

    private static boolean hasAbstractMethods(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      final Set<PsiMethod> overriddenMethods =
        calculateOverriddenMethods(methods);
      final PsiMethod[] allMethods = aClass.getAllMethods();
      for (final PsiMethod method : allMethods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !overriddenMethods.contains(method)) {
          return true;
        }
      }
      return false;
    }

    private static Set<PsiMethod> calculateOverriddenMethods(
      PsiMethod[] methods) {
      final Set<PsiMethod> overriddenMethods =
        new HashSet<>(methods.length);
      for (final PsiMethod method : methods) {
        calculateOverriddenMethods(method, overriddenMethods);
      }
      return overriddenMethods;
    }

    private static void calculateOverriddenMethods(
      PsiMethod method, Set<PsiMethod> overriddenMethods) {
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        overriddenMethods.add(superMethod);
      }
    }
  }
}