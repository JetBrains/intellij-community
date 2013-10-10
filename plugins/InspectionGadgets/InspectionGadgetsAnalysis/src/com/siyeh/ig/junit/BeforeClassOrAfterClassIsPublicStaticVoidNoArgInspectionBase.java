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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getID() {
    return "BeforeOrAfterWithIncorrectSignature";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "before.class.or.after.class.is.public.static.void.no.arg.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "before.class.or.after.class.is.public.static.void.no.arg.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor();
  }

  private static class BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      if (!TestUtils.isJUnit4BeforeClassOrAfterClassMethod(method)) {
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
      if (parameterList.getParametersCount() != 0 ||
          !returnType.equals(PsiType.VOID) ||
          !method.hasModifierProperty(PsiModifier.PUBLIC) ||
          !method.hasModifierProperty(PsiModifier.STATIC)) {
        registerMethodError(method, "Change signature of \'" +
                                    PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                               PsiFormatUtil.SHOW_NAME |
                                                               PsiFormatUtil.SHOW_MODIFIERS |
                                                               PsiFormatUtil.SHOW_PARAMETERS |
                                                               PsiFormatUtil.SHOW_TYPE,
                                                               PsiFormatUtil.SHOW_TYPE) +
                                    "\' to \'public static void " +
                                    method.getName() +
                                    "()\'");
      }
    }
  }
}
