/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
                                                                         AnnotationUtil.isAnnotated(method, "org.junit.Test", false))) {
                registerError(nameIdentifier, "Class #ref annotated @RunWith(JUnitPlatform.class) lacks test methods");
              }
            }
          }
        }
      }
    };
  }
}
