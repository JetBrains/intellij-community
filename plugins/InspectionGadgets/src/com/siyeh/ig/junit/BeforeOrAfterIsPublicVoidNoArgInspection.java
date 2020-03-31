/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class BeforeOrAfterIsPublicVoidNoArgInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return new MakePublicStaticVoidFix(method, false);
  }

  @Override
  @NotNull
  public String getID() {
    return "BeforeOrAfterWithIncorrectSignature";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "before.or.after.is.public.void.no.arg.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BeforeOrAfterIsPublicVoidNoArgVisitor();
  }

  private static class BeforeOrAfterIsPublicVoidNoArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      if (!TestUtils.isJUnit4BeforeOrAfterMethod(method)) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiClass targetClass = method.getContainingClass();
      if (targetClass == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (!parameterList.isEmpty() || !returnType.equals(PsiType.VOID) ||
          !method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.STATIC)) {
        registerMethodError(method, method);
      }
    }
  }
}
