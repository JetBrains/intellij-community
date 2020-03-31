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
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

/**
 * @author Bas Leijdekkers
 */
public class TestMethodIsPublicVoidNoArgInspection extends BaseInspection {

  public final List<String> ignorableAnnotations = new ArrayList<>(Collections.singletonList("mockit.Mocked"));

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.parameter.if.annotated.by"));
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[1];
    return new MakePublicStaticVoidFix(method, false);
  }

  @Override
  @NotNull
  public String getID() {
    return "TestMethodWithIncorrectSignature";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Problem problem = (Problem)infos[0];
    switch (problem) {
      case PARAMETER:
        return InspectionGadgetsBundle.message("test.method.is.public.void.no.arg.problem.descriptor1");
      case NOT_PUBLIC_VOID:
        return InspectionGadgetsBundle.message("test.method.is.public.void.no.arg.problem.descriptor2");
      case STATIC:
        return InspectionGadgetsBundle.message("test.method.is.public.void.no.arg.problem.descriptor3");
      default:
        throw new AssertionError();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestMethodIsPublicVoidNoArgVisitor();
  }

  enum Problem {
    STATIC, NOT_PUBLIC_VOID, PARAMETER
  }

  private class TestMethodIsPublicVoidNoArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor()) {
        return;
      }
      if (!TestUtils.isJUnit3TestMethod(method) && !TestUtils.isJUnit4TestMethod(method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, CHECK_HIERARCHY)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        registerMethodError(method, Problem.STATIC, method);
        return;
      }
      if (!parameterList.isEmpty()) {
        final PsiParameter[] parameters = parameterList.getParameters();
        boolean annotated = true;
        for (PsiParameter parameter : parameters) {
          if (!AnnotationUtil.isAnnotated(parameter, ignorableAnnotations, 0)) {
            annotated = false;
            break;
          }
        }
        if (!annotated) {
          registerMethodError(method, Problem.PARAMETER, method);
          return;
        }
      }
      final PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        registerMethodError(method, Problem.NOT_PUBLIC_VOID, method);
      }
    }
  }
}
