/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldStaticFinalFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class AtomicFieldUpdaterNotStaticFinalInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("atomic.field.updater.not.static.final.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    final PsiType type = field.getType();
    final String typeText = type.getPresentableText();
    return InspectionGadgetsBundle.message("atomic.field.updater.not.static.final.problem.descriptor", typeText);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return MakeFieldStaticFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AtomicFieldUpdaterNotStaticFinalVisitor();
  }

  private static class AtomicFieldUpdaterNotStaticFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiType type = field.getType();
      if (!InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicIntegerFieldUpdater") &&
        !InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicLongFieldUpdater") &&
        !InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicReferenceFieldUpdater")) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}
