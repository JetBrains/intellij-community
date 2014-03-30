/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestMethodIsPublicVoidNoArgInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "test.method.is.public.void.no.arg.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "TestMethodWithIncorrectSignature";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean isStatic = ((Boolean)infos[1]).booleanValue();
    if (isStatic) {
      return InspectionGadgetsBundle.message(
        "test.method.is.public.void.no.arg.problem.descriptor3");
    }
    final boolean takesArguments = ((Boolean)infos[0]).booleanValue();
    if (takesArguments) {
      return InspectionGadgetsBundle.message(
        "test.method.is.public.void.no.arg.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message(
        "test.method.is.public.void.no.arg.problem.descriptor2");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestMethodIsPublicVoidNoArgVisitor();
  }

  private static class TestMethodIsPublicVoidNoArgVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      @NonNls final String methodName = method.getName();
      if (!methodName.startsWith("test") &&
          !TestUtils.isJUnit4TestMethod(method)) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final boolean takesArguments;
      final boolean isStatic;
      if (parameterList.getParametersCount() == 0) {
        takesArguments = false;
        isStatic = method.hasModifierProperty(PsiModifier.STATIC);
        if (!isStatic && returnType.equals(PsiType.VOID) &&
            method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return;
        }
      }
      else {
        isStatic = false;
        takesArguments = true;
      }
      final PsiClass targetClass = method.getContainingClass();
      if (!AnnotationUtil.isAnnotated(method, "org.junit.Test", true)) {
        if (targetClass == null ||
            !InheritanceUtil.isInheritor(targetClass,
                                         "junit.framework.TestCase")) {
          return;
        }
      }
      registerMethodError(method, Boolean.valueOf(takesArguments),
                          Boolean.valueOf(isStatic));
    }
  }
}
