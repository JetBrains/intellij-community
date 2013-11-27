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

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;

public abstract class JUnitConfigurationProducer extends JavaRunConfigurationProducerBase<JUnitConfiguration> implements Cloneable {

  public JUnitConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return !other.isProducedBy(TestMethodConfigurationProducer.class);
  }

  @Override
  public boolean isConfigurationFromContext(JUnitConfiguration unitConfiguration, ConfigurationContext context) {
    if (PatternConfigurationProducer.isMultipleElementsSelected(context)) {
      return false;
    }
    final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(JUnitConfigurationType.getInstance());
    Location location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation());
    final PsiElement element = location.getPsiElement();
    final PsiClass testClass = JUnitUtil.getTestClass(element);
    final PsiMethod testMethod = JUnitUtil.getTestMethod(element, false);
    final PsiPackage testPackage;
    if (element instanceof PsiPackage) {
      testPackage = (PsiPackage)element;
    } else if (element instanceof PsiDirectory){
      testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    } else {
      testPackage = null;
    }
    PsiDirectory testDir = element instanceof PsiDirectory ? (PsiDirectory)element : null;
    RunnerAndConfigurationSettings template = RunManager.getInstance(location.getProject())
      .getConfigurationTemplate(getConfigurationFactory());
    final Module predefinedModule =
      ((JUnitConfiguration)template
        .getConfiguration()).getConfigurationModule().getModule();
    final String vmParameters = predefinedConfiguration instanceof JUnitConfiguration ? ((JUnitConfiguration)predefinedConfiguration).getVMParameters() : null;

    if (vmParameters != null && !Comparing.strEqual(vmParameters, unitConfiguration.getVMParameters())) return false;
    final TestObject testobject = unitConfiguration.getTestObject();
    if (testobject != null) {
      if (testobject.isConfiguredByElement(unitConfiguration, testClass, testMethod, testPackage, testDir)) {
        final Module configurationModule = unitConfiguration.getConfigurationModule().getModule();
        if (Comparing.equal(location.getModule(), configurationModule)) return true;
        if (Comparing.equal(predefinedModule, configurationModule)) {
          return true;
        }
      }
    }
    return false;
  }
}
