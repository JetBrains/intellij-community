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
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

public class AbstractClassNeverImplementedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "abstract.class.never.implemented.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "abstract.class.never.implemented.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractClassNeverImplementedVisitor();
  }

  private static class AbstractClassNeverImplementedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (InheritanceUtil.hasImplementation(aClass)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}