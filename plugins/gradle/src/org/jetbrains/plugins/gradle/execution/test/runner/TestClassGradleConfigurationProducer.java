/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.getMethodLocation;

/**
 * @author Vladislav.Soroka
 * @since 2/14/14
 */
public class TestClassGradleConfigurationProducer extends GradleTestRunConfigurationProducer {

  public TestClassGradleConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                    ConfigurationContext context,
                                                    Ref<PsiElement> sourceElement) {
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    if (RunConfigurationProducer.getInstance(PatternGradleConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    PsiClass testClass = getPsiClassForLocation(contextLocation);
    if (testClass == null) {
      return false;
    }
    sourceElement.set(testClass);

    final Module module = context.getModule();
    if (module == null) return false;

    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    final String projectPath = resolveProjectPath(module);
    if (projectPath == null) return false;

    List<String> tasksToRun = getTasksToRun(module);
    if (tasksToRun.isEmpty()) return false;

    configuration.getSettings().setExternalProjectPath(projectPath);
    configuration.getSettings().setTaskNames(tasksToRun);
    configuration.getSettings()
      .setScriptParameters(String.format("--tests %s", getRuntimeQualifiedName(testClass)));
    configuration.setName(testClass.getName());

    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Nullable
  protected PsiMethod getPsiMethodForLocation(Location contextLocation) {
    Location<PsiMethod> location = getMethodLocation(contextLocation);
    return location != null ? location.getPsiElement() : null;
  }

  @Nullable
  protected PsiClass getPsiClassForLocation(Location contextLocation) {
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return null;
    return JUnitUtil.getTestClass(location);
  }

  @Override
  protected boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    if (RunConfigurationProducer.getInstance(PatternGradleConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }

    if (getPsiMethodForLocation(contextLocation) != null) return false;
    PsiClass testClass = getPsiClassForLocation(contextLocation);
    if (testClass == null || testClass.getQualifiedName() == null) return false;

    if (context.getModule() == null) return false;

    final String projectPath = resolveProjectPath(context.getModule());
    if (projectPath == null) return false;
    if (!StringUtil.equals(projectPath, configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    if (!configuration.getSettings().getTaskNames().containsAll(getTasksToRun(context.getModule()))) return false;

    final String scriptParameters = configuration.getSettings().getScriptParameters() + ' ';
    int i = scriptParameters.indexOf("--tests ");
    if(i == -1) return false;

    String str = scriptParameters.substring(i + "--tests ".length()).trim() + ' ';
    return str.startsWith(getRuntimeQualifiedName(testClass) + ' ') && !str.contains("--tests");
  }

  @Override
  public void onFirstRun(final ConfigurationFromContext fromContext, final ConfigurationContext context, @NotNull final Runnable performRunnable) {
    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        if (!StringUtil.equals(ExternalSystemModulePropertyManager.getInstance(context.getModule()).getExternalSystemId(),
                               GradleConstants.SYSTEM_ID.toString())) {
          return;
        }

        ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)fromContext.getConfiguration();
        if (!applyTestConfiguration(configuration, context, ArrayUtil.toObjectArray(classes, PsiClass.class))) return;
        super.runForClasses(classes, method, context, performRunnable);
      }

      @Override
      protected void runForClass(PsiClass aClass,
                                 PsiMethod psiMethod,
                                 ConfigurationContext context,
                                 Runnable performRunnable) {
        if (!StringUtil.equals(
          ExternalSystemModulePropertyManager.getInstance(context.getModule()).getExternalSystemId(),
          GradleConstants.SYSTEM_ID.toString())) {
          return;
        }

        ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)fromContext.getConfiguration();
        if (!applyTestConfiguration(configuration, context, aClass)) return;
        super.runForClass(aClass, psiMethod, context, performRunnable);
      }
    };
    if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, null, (PsiClass)fromContext.getSourceElement())) return;
    super.onFirstRun(fromContext, context, performRunnable);
  }

  private static boolean applyTestConfiguration(@NotNull ExternalSystemRunConfiguration configuration,
                                                @NotNull ConfigurationContext context,
                                                @NotNull PsiClass... containingClasses) {
    final Module module = context.getModule();
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (projectPath == null) return false;

    List<String> tasksToRun = getTasksToRun(module);
    if (tasksToRun.isEmpty()) return false;

    configuration.getSettings().setExternalProjectPath(projectPath);
    configuration.getSettings().setTaskNames(tasksToRun);

    StringBuilder buf = new StringBuilder();
    for (PsiClass aClass : containingClasses) {
      buf.append(String.format("--tests %s ", getRuntimeQualifiedName(aClass)));
    }

    configuration.getSettings().setScriptParameters(buf.toString());
    configuration.setName(StringUtil.join(containingClasses, aClass -> aClass.getName(), "|"));
    return true;
  }

  public static String getRuntimeQualifiedName(PsiClass psiClass) {
    PsiElement parent = psiClass.getParent();
    if (parent instanceof PsiClass) {
      return getRuntimeQualifiedName((PsiClass)parent) + "$" + psiClass.getName();
    }
    else {
      return psiClass.getQualifiedName();
    }
  }
}
