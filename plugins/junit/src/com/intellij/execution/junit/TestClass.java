// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

class TestClass extends TestObject {
  TestClass(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    javaParameters.getProgramParametersList().add(data.getMainClassName());
    return javaParameters;
  }

  @Override
  protected void collectPackagesToOpen(List<String> options) {
    options.add(StringUtil.getPackageName(getConfiguration().getPersistentData().getMainClassName()));
  }

  @NotNull
  @Override
  protected String getForkMode() {
    String forkMode = super.getForkMode();
    return JUnitConfiguration.FORK_KLASS.equals(forkMode) ? JUnitConfiguration.FORK_REPEAT : forkMode;
  }

  @Override
  public String suggestActionName() {
    String name = getConfiguration().getPersistentData().MAIN_CLASS_NAME;
    if (name != null && name.endsWith(".")) {
      return name;
    }
    return JavaExecutionUtil.getShortClassName(name);
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element) {
    return RefactoringListeners.getClassOrPackageListener(element, getConfiguration().myClass);
  }

  @Override
  public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {

    if (testClass == null) {
      return false;
    }
    if (testMethod != null) {
      // 'test class' configuration is not equal to the 'test method' configuration!
      return false;
    }
    return Objects.equals(JavaExecutionUtil.getRuntimeQualifiedName(testClass), configuration.getPersistentData().getMainClassName());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String testClassName = getConfiguration().getPersistentData().getMainClassName();
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    final PsiClass testClass = configurationModule.checkModuleAndClassName(testClassName, JUnitBundle.message("no.test.class.specified.error.text"));
    TestFramework framework = TestFrameworks.detectFramework(testClass);
    if (framework == null || !framework.isTestClass(testClass)) {
      throw new RuntimeConfigurationWarning(JUnitBundle.message("class.not.test.error.message", testClassName));
    }
  }
}
