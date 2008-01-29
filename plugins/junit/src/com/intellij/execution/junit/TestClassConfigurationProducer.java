/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

public class TestClassConfigurationProducer extends JUnitConfigurationProducer {
  private PsiClass myTestClass;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(Location location, final ConfigurationContext context) {
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    final Project project = location.getProject();

    myTestClass = JUnitUtil.getTestClass(location);
    if (myTestClass == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final Module[] modules = configuration.getModules();
    final Module originalModule = modules == null || modules.length == 0 ? null : modules[0];
    configuration.beClassConfiguration(myTestClass);
    configuration.restoreOriginalModule(originalModule);
    configuration.setUpCoverageFilters();
    copyStepsBeforeRun(project, configuration);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myTestClass;
  }


}
