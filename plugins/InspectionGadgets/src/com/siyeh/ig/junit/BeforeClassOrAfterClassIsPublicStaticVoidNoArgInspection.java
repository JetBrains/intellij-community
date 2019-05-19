/*
 * Copyright 2006-2016 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspection extends
                                                                      BaseInspection {

  private static final String[] STATIC_CONFIGS = {
    "org.junit.BeforeClass",
    "org.junit.AfterClass",
    "org.junit.jupiter.api.BeforeAll",
    "org.junit.jupiter.api.AfterAll"
  };

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    String targetModifier = isJunit4Annotation((String)infos[1]) ? PsiModifier.PUBLIC : PsiModifier.PACKAGE_LOCAL;
    return new MakePublicStaticVoidFix(method, true, targetModifier);
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

  protected static boolean isJunit4Annotation(String annotation) {
    return annotation.endsWith("Class");
  }

  private static class BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      String annotation = Arrays.stream(STATIC_CONFIGS)
        .filter(anno -> AnnotationUtil.isAnnotated(method, anno, CHECK_HIERARCHY))
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
      boolean junit4Annotation = isJunit4Annotation(annotation);
      if (junit4Annotation && (!parameterList.isEmpty() || !method.hasModifierProperty(PsiModifier.PUBLIC)) ||
          !returnType.equals(PsiType.VOID) ||
          !method.hasModifierProperty(PsiModifier.STATIC) && (junit4Annotation || !TestUtils.testInstancePerClass(targetClass))) {
        registerMethodError(method, method, annotation);
      }
    }
  }
}
