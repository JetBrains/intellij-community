/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit.PatternConfigurationProducer;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

import static org.jetbrains.plugins.gradle.execution.test.runner.TestRunnerUtils.getMethodLocation;

/**
 * @author Vladislav.Soroka
 * @since 2/14/14
 */
public class TestMethodGradleConfigurationProducer extends GradleTestRunConfigurationProducer {

  private static final List<String> TASKS_TO_RUN = ContainerUtil.newArrayList("cleanTest", "test");

  public TestMethodGradleConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    Location<PsiMethod> methodLocation = getMethodLocation(contextLocation);
    if (methodLocation == null) return false;

    final PsiMethod psiMethod = methodLocation.getPsiElement();
    sourceElement.set(psiMethod);

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return false;

    if (context.getModule() == null) return false;

    if (!applyTestMethodConfiguration(configuration, context, psiMethod, containingClass)) return false;

    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    if (configuration == null) return false;
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;

    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    Location<PsiMethod> methodLocation = getMethodLocation(contextLocation);
    if (methodLocation == null) return false;

    final PsiMethod psiMethod = methodLocation.getPsiElement();

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return false;

    if (context.getModule() == null) return false;

    if (!StringUtil.equals(
      context.getModule().getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH),
      configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    if (!configuration.getSettings().getTaskNames().containsAll(TASKS_TO_RUN)) return false;

    final String scriptParameters = configuration.getSettings().getScriptParameters() + ' ';
    final String testFilter = creatTestFilter(containingClass, psiMethod);
    return scriptParameters.contains(testFilter);
  }

  @Override
  public void onFirstRun(final ConfigurationFromContext fromContext, final ConfigurationContext context, final Runnable performRunnable) {
    final PsiMethod psiMethod = (PsiMethod)fromContext.getSourceElement();
    final PsiClass containingClass = psiMethod.getContainingClass();
    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        if (!StringUtil.equals(
          context.getModule().getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID),
          GradleConstants.SYSTEM_ID.toString())) {
          return;
        }

        ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)fromContext.getConfiguration();
        if (!applyTestMethodConfiguration(configuration, context, psiMethod, ArrayUtil.toObjectArray(classes, PsiClass.class))) return;
        super.runForClasses(classes, method, context, performRunnable);
      }

      @Override
      protected void runForClass(PsiClass aClass,
                                 PsiMethod psiMethod,
                                 ConfigurationContext context,
                                 Runnable performRunnable) {
        if (!StringUtil.equals(
          context.getModule().getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID),
          GradleConstants.SYSTEM_ID.toString())) {
          return;
        }

        ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)fromContext.getConfiguration();
        if (!applyTestMethodConfiguration(configuration, context, psiMethod, aClass)) return;
        super.runForClass(aClass, psiMethod, context, performRunnable);
      }
    };
    if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass)) return;
    super.onFirstRun(fromContext, context, performRunnable);
  }

  private static boolean applyTestMethodConfiguration(@NotNull ExternalSystemRunConfiguration configuration,
                                                      @NotNull ConfigurationContext context,
                                                      @NotNull PsiMethod psiMethod,
                                                      @NotNull PsiClass... containingClasses) {
    if (!StringUtil.equals(
      context.getModule().getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID),
      GradleConstants.SYSTEM_ID.toString())) {
      return false;
    }

    configuration.getSettings().setExternalProjectPath(context.getModule().getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH));
    configuration.getSettings().setTaskNames(TASKS_TO_RUN);

    StringBuilder buf = new StringBuilder();
    for (PsiClass aClass : containingClasses) {
      buf.append(creatTestFilter(aClass, psiMethod));
    }

    configuration.getSettings().setScriptParameters(buf.toString().trim());
    configuration.setName(psiMethod.getName());
    return true;
  }

  private static String creatTestFilter(@NotNull PsiClass aClass, @NotNull PsiMethod psiMethod) {
    String testFilterPattern = aClass.getQualifiedName() + '.' + psiMethod.getName();
    return String.format("--tests \"%s\" ", StringUtil.replaceChar(testFilterPattern, '\"', '*'));
  }
}
