/*
 * Copyright 2009-2015 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldStaticFinalFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ThreadLocalNotStaticFinalInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("thread.local.not.static.final.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.local.not.static.final.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return MakeFieldStaticFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadLocalNotStaticFinalVisitor();
  }

  private static class ThreadLocalNotStaticFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      final PsiType type = field.getType();
      if (!InheritanceUtil.isInheritor(type, "java.lang.ThreadLocal")) {
        return;
      }
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      if (modifierList.hasModifierProperty(PsiModifier.STATIC) &&
          modifierList.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}