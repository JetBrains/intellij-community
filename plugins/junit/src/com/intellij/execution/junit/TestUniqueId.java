// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.Function;

import java.util.Arrays;

public class TestUniqueId extends TestObject {
  public TestUniqueId(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    addClassesListToJavaParameters(Arrays.asList(data.getUniqueIds()), getUniqueIdPresentation(), "", true, javaParameters);
    return javaParameters;
  }

  public static Function<String, String> getUniqueIdPresentation() {
    return s -> "\u001B" + s;
  }

  /**
   * Return nodeId for the cases where containing method or class do not represent tests (IDEA fails to detect them as tests),
   * or if parent node provides the same location, the case of parameterized/dynamic tests
   */
  public static String getEffectiveNodeId(AbstractTestProxy testInfo, Project project, GlobalSearchScope searchScope) {
    String nodeId = testInfo.getUserData(SMTestProxy.NODE_ID);
    if (nodeId != null) {
      Location location = testInfo.getLocation(project, searchScope);
      if (location == null) return nodeId;
      PsiElement psiElement = location.getPsiElement();
      PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
      if (method != null) {
        PsiClass containingClass = method.getContainingClass();
        TestFramework testFramework = containingClass != null ? TestFrameworks.detectFramework(containingClass) : null;
        if (testFramework == null || !testFramework.isTestMethod(psiElement)) {
          return nodeId;
        }
      }
      else {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
        if (containingClass == null || TestFrameworks.detectFramework(containingClass) == null) {
          return nodeId;
        }
      }

      AbstractTestProxy parent = testInfo.getParent();
      if (parent != null) {
        Location parentLocation = parent.getLocation(project, searchScope);
        if (parentLocation != null && parentLocation.getPsiElement() == psiElement) {
          return nodeId;
        }
      }
    }
    return null;
  }

  @Override
  public String suggestActionName() {
    String[] ids = getConfiguration().getPersistentData().getUniqueIds();
    return JavaExecutionUtil.getShortClassName(ids.length > 0 ? ids[0] : "<empty>");
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return null;
  }

  @Override
  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {

    return false;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    String[] ids = getConfiguration().getPersistentData().getUniqueIds();
    if (ids == null || ids.length == 0) {
      throw new RuntimeConfigurationException("No unique id specified");
    }
  }
}
