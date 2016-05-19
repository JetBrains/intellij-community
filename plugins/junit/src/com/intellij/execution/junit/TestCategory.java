/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
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

/**
* User: anna
* Date: 4/21/11
*/
class TestCategory extends TestPackage {
  public TestCategory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
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
      throw new RuntimeConfigurationError("Category is not specified");
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
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return RefactoringListeners.getClassOrPackageListener(element, configuration.myCategory);
  }
}
