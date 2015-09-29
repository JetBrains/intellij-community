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

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.Iterator;
import java.util.List;

public abstract class AbstractTestMethodConfigurationProducer extends JUnitConfigurationProducer {
  
  protected AbstractTestMethodConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    Location<PsiMethod> methodLocation = getTestMethod(contextLocation);
    if (methodLocation == null) return false;

    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(paramSetName);
      }
      PsiClass containingClass = ((PsiMemberParameterizedLocation)contextLocation).getContainingClass();
      if (containingClass != null) {
        methodLocation = MethodLocation.elementInClass(methodLocation.getPsiElement(), containingClass);
      }
    }
    sourceElement.set(methodLocation.getPsiElement());
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beMethodConfiguration(methodLocation);
    configuration.restoreOriginalModule(originalModule);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    configuration.setForkMode(JUnitConfiguration.FORK_NONE);
    return true;
  }

  private static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation, false)) return methodLocation;
    }
    return null;
  }

  @Override
  public void onFirstRun(final ConfigurationFromContext fromContext, final ConfigurationContext context, final Runnable performRunnable) {
    final PsiMethod psiMethod = (PsiMethod)fromContext.getSourceElement();
    final PsiClass containingClass = psiMethod.getContainingClass();
    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        ((JUnitConfiguration)fromContext.getConfiguration()).bePatternConfiguration(classes, method);
        super.runForClasses(classes, method, context, performRunnable);
      }

      @Override
      protected void runForClass(PsiClass aClass,
                                 PsiMethod psiMethod,
                                 ConfigurationContext context,
                                 Runnable performRunnable) {
        final Project project = psiMethod.getProject();
        final MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
        ((JUnitConfiguration)fromContext.getConfiguration()).beMethodConfiguration(methodLocation);
        super.runForClass(aClass, psiMethod, context, performRunnable);
      }
    };
    if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass, getConditionToSearchForInheritors())) return;
    super.onFirstRun(fromContext, context, performRunnable);
  }
}

