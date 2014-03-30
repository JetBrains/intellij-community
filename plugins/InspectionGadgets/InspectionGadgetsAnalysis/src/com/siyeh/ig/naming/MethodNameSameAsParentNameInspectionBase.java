/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class MethodNameSameAsParentNameInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.name.same.as.parent.name.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.name.same.as.parent.name.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNameSameAsParentClassNameVisitor();
  }

  private static class MethodNameSameAsParentClassNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // no call to super, so it doesn't drill down into inner classes
      if (method.isConstructor()) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiClass parent = containingClass.getSuperClass();
      if (parent == null) {
        return;
      }
      final String parentName = parent.getName();
      if (parentName == null) {
        return;
      }
      final String methodName = method.getName();
      if (!methodName.equals(parentName)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
