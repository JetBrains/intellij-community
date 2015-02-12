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
package com.siyeh.ig.cloneable;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NonPublicCloneInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.public.clone.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.public.clone.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.PUBLIC);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonPublicCloneVisitor();
  }

  private static class NonPublicCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.PUBLIC) || !CloneUtils.isClone(method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!CloneUtils.isCloneable(containingClass)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
