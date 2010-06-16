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

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class JUnitConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {
  public static final RuntimeConfigurationProducer[] PROTOTYPES = new RuntimeConfigurationProducer[]{
        new AllInPackageConfigurationProducer(),
        new TestMethodConfigurationProducer(),
        new TestClassConfigurationProducer()};

  public JUnitConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  
  public int compareTo(final Object o) {
    if (o instanceof TestMethodConfigurationProducer) return -PREFERED;
    return PREFERED;
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(@NotNull Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext());
    if (elements != null && elements.length > 1) {
      return null;
    }
    final Module predefinedModule =
      ((JUnitConfiguration)((RunManagerImpl)RunManagerEx.getInstanceEx(location.getProject()))
        .getConfigurationTemplate(getConfigurationFactory())
        .getConfiguration()).getConfigurationModule().getModule();
    final PsiElement element = location.getPsiElement();
    final PsiClass testClass = JUnitUtil.getTestClass(element);
    final PsiMethod testMethod = JUnitUtil.getTestMethod(element);
    final PsiPackage testPackage;
    if (element instanceof PsiPackage) {
      testPackage = (PsiPackage)element;
    } else if (element instanceof PsiDirectory){
      testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    } else {
      testPackage = null;
    }
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      final JUnitConfiguration unitConfiguration = (JUnitConfiguration)existingConfiguration.getConfiguration();
      final TestObject testobject = unitConfiguration.getTestObject();
      if (testobject != null) {
        if (testobject.isConfiguredByElement(unitConfiguration, testClass, testMethod, testPackage)) {
          final Module configurationModule = unitConfiguration.getConfigurationModule().getModule();
          if (Comparing.equal(location.getModule(), configurationModule)) return existingConfiguration;
          if (Comparing.equal(predefinedModule, configurationModule)) {
            return existingConfiguration;
          }
        }
      }
    }
    return null;
  }
}
