// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class JUnit5PlatformInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit5.platform.runner.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
        if (nameIdentifier != null && PsiClassUtil.isRunnableClass(aClass, true, false)) {
          PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, "org.junit.runner.RunWith");
          if (annotation != null) {
            final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
            if (value instanceof PsiClassObjectAccessExpression) {
              final PsiTypeElement operand = ((PsiClassObjectAccessExpression)value).getOperand();
              final PsiClass runnerClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
              if (runnerClass != null && "org.junit.platform.runner.JUnitPlatform".equals(runnerClass.getQualifiedName()) &&
                  Arrays.stream(aClass.getMethods()).noneMatch(method -> method.hasModifierProperty(PsiModifier.PUBLIC) &&
                                                                         method.getParameterList().getParametersCount() == 0 &&
                                                                         AnnotationUtil.isAnnotated(method, "org.junit.Test", 0))) {
                registerError(nameIdentifier, "Class #ref annotated @RunWith(JUnitPlatform.class) lacks test methods");
              }
            }
          }
        }
      }
    };
  }
}
