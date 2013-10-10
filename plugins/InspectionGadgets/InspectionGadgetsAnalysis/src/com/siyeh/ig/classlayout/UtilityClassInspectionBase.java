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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

public class UtilityClassInspectionBase extends BaseInspection {
  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("utility.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "utility.class.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner)infos[0], ignorableAnnotations);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UtilityClassVisitor();
  }

  private class UtilityClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
