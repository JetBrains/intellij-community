/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.InheritorChooser;
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
public class TestMethodGradleConfigurationProducer extends GradleTestRunConfigurationProducer {

  public TestMethodGradleConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                    ConfigurationContext context,
                                                    Ref<PsiElement> sourceElement) {
    if (RunConfigurationProducer.getInstance(PatternGradleConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    PsiMethod psiMethod = getPsiMethodForLocation(contextLocation);
    if (psiMethod == null) return false;
    sourceElement.set(psiMethod);

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return false;

    if (context.getModule() == null) return false;

    if (!applyTestMethodConfiguration(configuration, context, psiMethod, containingClass)) return false;

    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Nullable
  protected PsiMethod getPsiMethodForLocation(Location contextLocation) {
    Location<PsiMethod> location = getMethodLocation(contextLocation);
    return location != null ? location.getPsiElement() : null;
  }

  @Override
  protected boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    if (RunConfigurationProducer.getInstance(PatternGradleConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    PsiMethod psiMethod = getPsiMethodForLocation(contextLocation);
    if (psiMethod == null) return false;

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return false;

    final Module module = context.getModule();
    if (module == null) return false;

    final String projectPath = resolveProjectPath(module);
    if (projectPath == null) return false;

    if (!StringUtil.equals(projectPath, configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    if (!configuration.getSettings().getTaskNames().containsAll(getTasksToRun(module))) return false;

    final String scriptParameters = configuration.getSettings().getScriptParameters() + ' ';
    final String testFilter = createTestFilter(containingClass, psiMethod);
    return scriptParameters.contains(testFilter);
  }

  @Override
  public void onFirstRun(@NotNull final ConfigurationFromContext fromContext, @NotNull final ConfigurationContext context, @NotNull final Runnable performRunnable) {
    final PsiMethod psiMethod = (PsiMethod)fromContext.getSourceElement();
    final PsiClass containingClass = psiMethod.getContainingClass();
    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        if (!StringUtil.equals(
          ExternalSystemModulePropertyManager.getInstance(context.getModule()).getExternalSystemId(),
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
          ExternalSystemModulePropertyManager.getInstance(context.getModule()).getExternalSystemId(),
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

  private boolean applyTestMethodConfiguration(@NotNull ExternalSystemRunConfiguration configuration,
                                               @NotNull ConfigurationContext context,
                                               @NotNull PsiMethod psiMethod,
                                               @NotNull PsiClass... containingClasses) {
    final Module module = context.getModule();
    if (module == null) return false;

    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    final String projectPath = resolveProjectPath(module);
    if (projectPath == null) return false;

    List<String> tasksToRun = getTasksToRun(module);
    if (tasksToRun.isEmpty()) return false;

    configuration.getSettings().setExternalProjectPath(projectPath);
    configuration.getSettings().setTaskNames(tasksToRun);

    StringBuilder buf = new StringBuilder();
    for (PsiClass aClass : containingClasses) {
      final String filter = createTestFilter(aClass, psiMethod);
      if(filter != null) {
        buf.append(filter);
      }
    }

    configuration.getSettings().setScriptParameters(buf.toString().trim());
    configuration.setName((containingClasses.length == 1 ? containingClasses[0].getName() + "." : "") + psiMethod.getName());
    return true;
  }

  @Nullable
  private static String createTestFilter(@NotNull PsiClass aClass, @NotNull PsiMethod psiMethod) {
    return createTestFilter(TestClassGradleConfigurationProducer.getRuntimeQualifiedName(aClass), psiMethod.getName());
  }

  @Nullable
  public static String createTestFilter(@Nullable String aClass, @Nullable String method) {
    if (aClass == null) return null;
    String testFilterPattern = aClass + (method == null ? "" : '.' + method);
    return String.format("--tests \"%s\" ", testFilterPattern.replace('\"', '*'));
  }
}
