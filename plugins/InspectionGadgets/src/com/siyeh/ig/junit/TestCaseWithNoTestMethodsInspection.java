/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class TestCaseWithNoTestMethodsInspection extends ClassInspection {

  public String getID() {
    return "JUnitTestCaseWithNoTests";
  }

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithNoTestMethodsVisitor();
  }

  private static class TestCaseWithNoTestMethodsVisitor extends BaseInspectionVisitor {


    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface()
          || aClass.isEnum()
          || aClass.isAnnotationType()
          || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiAnonymousClass) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (isTest(method)) {
          return;
        }
      }
      if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
        return;
      }
      registerClassError(aClass);
    }

    private boolean isTest(PsiMethod method) {
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }

      final PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType)) {
        return false;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 0) {
        return false;
      }
      @NonNls final String name = method.getName();
      return name.startsWith("test");

    }

  }
}
