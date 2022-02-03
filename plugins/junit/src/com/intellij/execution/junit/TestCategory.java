// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.NotNull;

class TestCategory extends TestPackage {
  TestCategory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected GlobalSearchScope filterScope(JUnitConfiguration.Data data) throws CantRunException {
    return GlobalSearchScope.allScope(getConfiguration().getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String category = getConfiguration().getPersistentData().getCategory();
    if (category == null || category.isEmpty()) {
      throw new RuntimeConfigurationError(JUnitBundle.message("category.is.not.specified.error.message"));
    }
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    if (getSourceScope() == null) {
      configurationModule.checkForWarning();
    }
    final Module module = configurationModule.getModule();
    if (module != null) {
      final PsiClass psiClass = JavaExecutionUtil.findMainClass(getConfiguration().getProject(), category, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (psiClass == null) {
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("class.not.found.in.module.error.message", category, configurationModule.getModuleName()));
      }
    }
  }

  @Override
  protected @NotNull String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return "";
  }

  @Override
  public String suggestActionName() {
    return JUnitBundle.message("action.text.test.category", getConfiguration().getPersistentData().getCategory());
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
  public RefactoringElementListener getListener(final PsiElement element) {
    return RefactoringListeners.getClassOrPackageListener(element, getConfiguration().myCategory);
  }
}
