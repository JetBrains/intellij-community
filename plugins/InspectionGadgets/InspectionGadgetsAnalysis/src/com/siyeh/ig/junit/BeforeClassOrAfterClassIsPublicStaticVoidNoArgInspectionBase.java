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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase extends BaseInspection {
  private static final String[] STATIC_CONFIGS = new String[] {
    "org.junit.BeforeClass",
    "org.junit.AfterClass",
    "org.junit.jupiter.api.BeforeAll",
    "org.junit.jupiter.api.AfterAll"
  };

  protected static boolean isJunit4Annotation(String annotation) {
    return annotation.endsWith("Class");
  }

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
      "before.class.or.after.class.is.public.static.void.no.arg.problem.descriptor", infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor();
  }

  private static class BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      String annotation = Arrays.stream(STATIC_CONFIGS)
        .filter(anno -> AnnotationUtil.isAnnotated(method, anno, true))
        .findFirst().orElse(null);
      if (annotation == null) {
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
      if (isJunit4Annotation(annotation) && (parameterList.getParametersCount() != 0 || !method.hasModifierProperty(PsiModifier.PUBLIC)) ||
          !returnType.equals(PsiType.VOID) || !method.hasModifierProperty(PsiModifier.STATIC)) {
        registerMethodError(method, method, annotation);
      }
    }
  }
}
