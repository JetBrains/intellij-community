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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class NonFinalUtilityClassInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.final.utility.class.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.final.utility.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonFinalUtilityClassVisitor();
  }

  private static class NonFinalUtilityClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.FINAL) ||
        aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
