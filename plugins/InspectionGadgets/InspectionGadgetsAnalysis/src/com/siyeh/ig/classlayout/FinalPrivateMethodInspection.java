/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class FinalPrivateMethodInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "final.private.method.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "final.private.method.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalPrivateMethodVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  private static class FinalPrivateMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.hasModifierProperty(PsiModifier.FINAL)
          || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_SAFE_VARARGS, 0) && method.isVarArgs()) {
        return;
      }
      registerModifierError(PsiModifier.FINAL, method, PsiModifier.FINAL);
    }
  }
}