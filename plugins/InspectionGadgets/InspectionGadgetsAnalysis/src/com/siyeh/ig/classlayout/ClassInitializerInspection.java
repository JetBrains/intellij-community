/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ChangeModifierFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassInitializerInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "NonStaticInitializer";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.initializer.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.initializer.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.STATIC);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassInitializerVisitor();
  }

  private static class ClassInitializerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerClassInitializerError(initializer);
    }
  }
}