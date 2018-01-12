/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;

import java.util.Collections;

class TestTags extends TestObject {
  public TestTags(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }


  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String[] tags = getConfiguration().getPersistentData().getTags();
    if (tags == null || tags.length == 0) {
      throw new RuntimeConfigurationError("Tags are not specified");
    }
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    if (getSourceScope() == null) {
      configurationModule.checkForWarning();
    }
  }

  @Override
  public String suggestActionName() {
    return "Tests of " + getConfiguration().getPersistentData().getCategory();
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    //tags written automatically inside
    addClassesListToJavaParameters(Collections.emptyList(), s -> "", "", true, javaParameters);
    return javaParameters;
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return null;
  }
}
