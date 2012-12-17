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
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.List;

public class TestClassConfigurationProducer extends JUnitConfigurationProducer {
  private PsiClass myTestClass;

  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, final ConfigurationContext context) {
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) return null;
    final Project project = location.getProject();

    if (PatternConfigurationProducer.isMultipleElementsSelected(context)) {
      return null;
    }
    myTestClass = JUnitUtil.getTestClass(location);
    if (myTestClass == null) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beClassConfiguration(myTestClass);
    configuration.restoreOriginalModule(originalModule);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myTestClass;
  }

  @Override
  public void perform(final ConfigurationContext context, final Runnable performRunnable) {

    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        ((JUnitConfiguration)context.getConfiguration().getConfiguration()).bePatternConfiguration(classes, method);
        super.runForClasses(classes, method, context, performRunnable);
      }

      @Override
      protected void runForClass(PsiClass aClass,
                                 PsiMethod psiMethod,
                                 ConfigurationContext context,
                                 Runnable performRunnable) {
        ((JUnitConfiguration)context.getConfiguration().getConfiguration()).beClassConfiguration(aClass);
        super.runForClass(aClass, psiMethod, context, performRunnable);
      }
    };
    if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, null, myTestClass)) return;
    super.perform(context, performRunnable);
  }
}
