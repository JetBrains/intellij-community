// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;

import java.util.List;
import java.util.Objects;

public class TestMethod extends TestObject {
  TestMethod(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = createDefaultJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    javaParameters.getProgramParametersList().add(data.getMainClassName() + "," + data.getMethodNameWithSignature());
    return javaParameters;
  }

  @Override
  protected @NotNull String getForkMode() {
    String forkMode = super.getForkMode();
    return JUnitConfiguration.FORK_METHOD.equals(forkMode) ? JUnitConfiguration.FORK_REPEAT : forkMode;
  }

  @Override
  protected void collectPackagesToOpen(List<String> options) {
    options.add(StringUtil.getPackageName(getConfiguration().getPersistentData().getMainClassName()));
  }

  protected JavaParameters createDefaultJavaParameters() throws ExecutionException {
    return super.createJavaParameters();
  }

  @Override
  public String suggestActionName() {
    return ProgramRunnerUtil.shortenName(getConfiguration().getPersistentData().getMethodName(), 2) + "()";
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element) {
    UElement uElement = UastContextKt.toUElement(element);
    JUnitConfiguration configuration = getConfiguration();
    if (uElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)uElement;
      if (!method.getName().equals(configuration.getPersistentData().getMethodName())) return null;
      //noinspection ConstantConditions
      if (!method.getContainingClass().equals(configuration.myClass.getPsiElement())) return null;
      class Listener extends RefactoringElementAdapter implements UndoRefactoringElementListener {
        @Override
        public void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
          final boolean generatedName = configuration.isGeneratedName();
          configuration.getPersistentData().setTestMethod(PsiLocation.fromPsiElement((PsiMethod)UastContextKt.toUElement(newElement)));
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
      Objects.equals(JavaExecutionUtil.getRuntimeQualifiedName(testClass), data.getMainClassName()) &&
      Objects.equals(JUnitConfiguration.Data.getMethodPresentation(testMethod), data.getMethodNameWithSignature());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final PsiClass psiClass = checkClass();

    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String methodName = data.getMethodName();
    String methodNameWithSignature = data.getMethodNameWithSignature();
    if (methodName == null || methodName.trim().length() == 0) {
      throw new RuntimeConfigurationError(JUnitBundle.message("method.name.not.specified.error.message"));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(psiClass);
    boolean found = false;
    for (final PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (filter.value(method) && Objects.equals(methodNameWithSignature, JUnitConfiguration.Data.getMethodPresentation(method))) {
        found = true;
      }
    }
    if (!found) {
      throw new RuntimeConfigurationWarning(JUnitBundle.message("test.method.doesnt.exist.error.message", methodName));
    }
  }

    @NotNull
  public PsiClass checkClass() throws RuntimeConfigurationException {
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String testClass = data.getMainClassName();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(testClass, JUnitBundle.message("no.test.class.specified.error.text"));

    TestFramework testFramework = TestFrameworks.detectFramework(psiClass);
    if (testFramework == null || !testFramework.isTestClass(psiClass)) {
      throw new RuntimeConfigurationError(JUnitBundle.message("class.not.test.error.message", testClass));
    }
    return psiClass;
  }
}
