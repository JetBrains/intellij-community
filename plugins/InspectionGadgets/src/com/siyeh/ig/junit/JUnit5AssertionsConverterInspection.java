/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.testFrameworks.AssertHint;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JUnit5AssertionsConverterInspection extends BaseInspection {
  private String myFrameworkName = "JUnit5";;

  JUnit5AssertionsConverterInspection(String frameworkName) {
    myFrameworkName = frameworkName;
  }

  public JUnit5AssertionsConverterInspection() {
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit5.assertions.converter.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    String name = (String)infos[0];
    String targetClassName = (String)infos[1];
    return InspectionGadgetsBundle.message("junit5.assertions.converter.problem.descriptor", name, targetClassName);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    boolean disabledFix = ((Boolean)infos[2]).booleanValue();
    return !disabledFix ? new ReplaceObsoleteAssertsFix((String)infos[1]) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfObsoleteAssertVisitor();
  }

  private class UseOfObsoleteAssertVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final Project project = expression.getProject();
      final Module module = ModuleUtilCore.findModuleForPsiElement(expression);
      if (module == null) {
        return;
      }
      final PsiClass newAssertClass = JavaPsiFacade.getInstance(project)
        .findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (newAssertClass == null) {
        return;
      }

      AssertHint hint = AssertHint.create(expression, methodName ->
        AssertHint.JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT.get(methodName), false);
      if (hint == null) {
        return;
      }

      final PsiMethod psiMethod = hint.getMethod();
      if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }

      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) {
        return;
      }

      final String name = containingClass.getQualifiedName();
      if (hint.isMessageOnFirstPosition()) {
        PsiFile file = expression.getContainingFile();
        if (file instanceof PsiClassOwner) {
          for (PsiClass psiClass : ((PsiClassOwner)file).getClasses()) {
            TestFramework testFramework = TestFrameworks.detectFramework(psiClass);
            if (testFramework != null && myFrameworkName.equals(testFramework.getName())) {
              String methodName = psiMethod.getName();
              registerMethodCallError(expression, name,
                                      getNewAssertClassName(methodName),
                                      absentInJUnit5(psiMethod, methodName));
              break;
            }
          }
        }

      }
    }

    private boolean absentInJUnit5(PsiMethod psiMethod, String methodName) {
      if ("fail".equals(methodName)) {
        return psiMethod.getParameterList().getParametersCount() == 0;
      }
      if ("assertNotEquals".equals(methodName)) {
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        if (parameters.length > 0) {
          int lastParamIdx = parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) ? 3 : 2;
          if (parameters.length > lastParamIdx && parameters[lastParamIdx].getType() instanceof PsiPrimitiveType) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private static String getNewAssertClassName(String methodName) {
    if ("assertThat".equals(methodName)) {
      return JUnitCommonClassNames.ORG_HAMCREST_MATCHER_ASSERT;
    }
    else if (methodName.startsWith("assume")) {
      return JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS;
    }
    else {
      return JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS;
    }
  }

  static class ReplaceObsoleteAssertsFix extends InspectionGadgetsFix {
    private final String myBaseClassName;

    public ReplaceObsoleteAssertsFix(String baseClassName) {
      myBaseClassName = baseClassName;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCallExpression =
        PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (methodCallExpression == null) {
        return;
      }

      AssertHint assertHint =
        AssertHint.create(methodCallExpression, methodName -> AssertHint.JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT
          .get(methodName), false);
      if (assertHint == null) {
        return;
      }

      String methodName = assertHint.getMethod().getName();
      PsiClass newAssertClass = JavaPsiFacade.getInstance(project).findClass(getNewAssertClassName(methodName),
                                                                             methodCallExpression.getResolveScope());

      if (!"assertThat".equals(methodName)) {
        PsiExpression message = assertHint.getMessage();
        if (message != null) {
          methodCallExpression.getArgumentList().add(message);
          message.delete();
        }
      }

      if (newAssertClass == null) {
        return;
      }
      String qualifiedName = newAssertClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }

      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      methodExpression.setQualifierExpression(JavaPsiFacade.getElementFactory(project).createReferenceExpression(newAssertClass));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(methodExpression);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("junit5.assertions.converter.quickfix", myBaseClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("junit5.assertions.converter.familyName");
    }
  }
}