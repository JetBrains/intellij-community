/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FeatureEnvyInspectionBase extends BaseInspection {

  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreTestCases = false; // keep for compatibility

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("feature.envy.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String className = element.getName();
    return InspectionGadgetsBundle.message("feature.envy.problem.descriptor", className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FeatureEnvyVisitor();
  }

  private static class FeatureEnvyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      final ClassAccessVisitor visitor = new ClassAccessVisitor(containingClass);
      method.accept(visitor);
      final Set<PsiClass> overAccessedClasses = visitor.getOveraccessedClasses();
      for (PsiClass aClass : overAccessedClasses) {
        registerMethodError(method, aClass, method);
      }
    }
  }
}