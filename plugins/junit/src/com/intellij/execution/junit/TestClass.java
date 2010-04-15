/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;

class TestClass extends TestObject {
  public TestClass(final Project project,
                   final JUnitConfiguration configuration,
                   RunnerSettings runnerSettings,
                   ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
  }

  protected void initialize() throws ExecutionException {
    super.initialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    RunConfigurationModule module = myConfiguration.getConfigurationModule();
    configureModule(myJavaParameters, module, data.getMainClassName());
    final Project project = module.getProject();
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(project, data.getMainClassName(), GlobalSearchScope.allScope(project));
    if (JUnitUtil.isJUnit4TestClass(psiClass)) {
      myJavaParameters.getProgramParametersList().add(JUnitStarter.JUNIT4_PARAMETER);
    }
    myJavaParameters.getProgramParametersList().add(data.getMainClassName());
  }

  public String suggestActionName() {
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(myConfiguration.getPersistentData().MAIN_CLASS_NAME), 0);
  }

  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
  }

  public boolean isConfiguredByElement(final JUnitConfiguration configuration, final PsiElement element) {
    final PsiClass aClass = JUnitUtil.getTestClass(element);
    if (aClass == null) {
      return false;
    }
    final PsiMethod method = JUnitUtil.getTestMethod(element);
    if (method != null) {
      // 'test class' configuration is not equal to the 'test method' configuration!
      return false;
    }
    return Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), configuration.getPersistentData().getMainClassName());
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String testClassName = myConfiguration.getPersistentData().getMainClassName();
    final JavaRunConfigurationModule configurationModule = myConfiguration.getConfigurationModule();
    final PsiClass testClass = configurationModule.checkModuleAndClassName(testClassName, ExecutionBundle.message("no.test.class.specified.error.text"));
    if (!JUnitUtil.isTestClass(testClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", testClassName));
    }
  }
}
