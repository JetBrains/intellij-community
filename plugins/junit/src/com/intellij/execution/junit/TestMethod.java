// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import org.jetbrains.annotations.NotNull;

class TestMethod extends TestObject {
  public TestMethod(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = createDefaultJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    javaParameters.getProgramParametersList().add(data.getMainClassName() + "," + data.getMethodNameWithSignature());
    return javaParameters;
  }

  protected JavaParameters createDefaultJavaParameters() throws ExecutionException {
    return super.createJavaParameters();
  }

  @Override
  public String suggestActionName() {
    return ProgramRunnerUtil.shortenName(getConfiguration().getPersistentData().METHOD_NAME, 2) + "()";
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (!method.getName().equals(configuration.getPersistentData().getMethodName())) return null;
      //noinspection ConstantConditions
      if (!method.getContainingClass().equals(configuration.myClass.getPsiElement())) return null;
      class Listener extends RefactoringElementAdapter implements UndoRefactoringElementListener {
        @Override
        public void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
          final boolean generatedName = configuration.isGeneratedName();
          configuration.getPersistentData().setTestMethod(PsiLocation.fromPsiElement((PsiMethod)newElement));
          if (generatedName) configuration.setGeneratedName();
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          final int methodIdx = oldQualifiedName.indexOf("#") + 1;
          if (methodIdx <= 0 || methodIdx >= oldQualifiedName.length()) return;
          final boolean generatedName = configuration.isGeneratedName();
          configuration.getPersistentData().METHOD_NAME = oldQualifiedName.substring(methodIdx);
          if (generatedName) configuration.setGeneratedName();
        }
      }
      return new Listener();
    }
    else {
      return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
    }
  }


  @Override
  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    if (testMethod == null) return false;
    if (testClass == null) return false;
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    /*final PsiClass containingClass = testMethod.getContainingClass();
    if (testClass == null && (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) return false;

    if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Comparing.equal(testMethod.getName(), data.getMethodName());
    }*/
    return
      Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(testClass), data.getMainClassName()) &&
      Comparing.equal(JUnitConfiguration.Data.getMethodPresentation(testMethod), data.getMethodNameWithSignature());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String testClass = data.getMainClassName();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(testClass, ExecutionBundle.message("no.test.class.specified.error.text"));

    final String methodName = data.getMethodName();
    String methodNameWithSignature = data.getMethodNameWithSignature();
    if (methodName == null || methodName.trim().length() == 0) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("method.name.not.specified.error.message"));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(psiClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (filter.value(method) && Comparing.equal(methodNameWithSignature, JUnitConfiguration.Data.getMethodPresentation(method))) {
        found = true;
      }
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("test.method.doesnt.exist.error.message", methodName));
    }

    if (!AnnotationUtil.isAnnotated(psiClass, JUnitUtil.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!psiClass.isInheritor(testCaseClass, true)) {
          throw new RuntimeConfigurationError(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", testClass));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("junit.jar.not.found.in.module.class.path.error.message", configurationModule.getModuleName()));
      }
    }
  }
}
