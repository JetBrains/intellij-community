// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil;

import java.util.List;

import static com.intellij.openapi.externalSystem.service.ExternalSystemTaskExecutionSettingsUtilKt.isSameSettings;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.getMethodLocation;
import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.applyTestConfigurationFor;

/**
 * @author Vladislav.Soroka
 */
public class TestMethodGradleConfigurationProducer extends GradleTestRunConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
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

    final Project project = context.getProject();
    final ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    if (!applyTestConfigurationFor(project, settings, contextLocation, psiMethod, containingClass)) {
      return false;
    }
    return isSameSettings(settings, configuration.getSettings());
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

  private static boolean applyTestMethodConfiguration(@NotNull ExternalSystemRunConfiguration configuration,
                                                      @NotNull ConfigurationContext context,
                                                      @NotNull PsiMethod psiMethod,
                                                      @NotNull PsiClass... containingClasses) {
    final Project project = context.getProject();
    final Location location = context.getLocation();
    assert location != null;
    final ExternalSystemTaskExecutionSettings settings = configuration.getSettings();
    if (!applyTestConfigurationFor(project, settings, location, psiMethod, containingClasses)) return false;
    configuration.setName((containingClasses.length == 1 ? containingClasses[0].getName() + "." : "") + psiMethod.getName());
    return true;
  }

  @NotNull
  public static String createTestFilter(@Nullable String aClass, @Nullable String method) {
    return GradleExecutionSettingsUtil.createTestFilterFromMethod(aClass, method);
  }
}
