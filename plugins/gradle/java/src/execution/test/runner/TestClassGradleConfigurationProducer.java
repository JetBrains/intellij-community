// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
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
import com.intellij.psi.*;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Iterator;
import java.util.List;

import static com.intellij.openapi.externalSystem.service.ExternalSystemTaskExecutionSettingsUtilKt.isSameSettings;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.getMethodLocation;
import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.applyTestConfigurationFor;

/**
 * @author Vladislav.Soroka
 */
public class TestClassGradleConfigurationProducer extends GradleTestRunConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
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

    if (!applyTestClassConfiguration(configuration, context, testClass)) return false;
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
    final Location<?> location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return null;

    TestFrameworks testFrameworks = TestFrameworks.getInstance();
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false); iterator.hasNext(); ) {
      final Location<PsiClass> classLocation = iterator.next();
      if (testFrameworks.isTestClass(classLocation.getPsiElement())) return classLocation.getPsiElement();
    }
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length == 1 && testFrameworks.isTestClass(classes[0])) return classes[0];
    }
    return null;
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

    final Project project = context.getProject();
    final ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    if (!applyTestConfigurationFor(project, settings, testClass)) return false;
    return isSameSettings(settings, configuration.getSettings());
  }

  @Override
  public void onFirstRun(@NotNull final ConfigurationFromContext fromContext, @NotNull final ConfigurationContext context, @NotNull final Runnable performRunnable) {
    final InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        if (!StringUtil.equals(ExternalSystemModulePropertyManager.getInstance(context.getModule()).getExternalSystemId(),
                               GradleConstants.SYSTEM_ID.toString())) {
          return;
        }

        ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)fromContext.getConfiguration();
        if (!applyTestClassConfiguration(configuration, context, ArrayUtil.toObjectArray(classes, PsiClass.class))) return;
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
        if (!applyTestClassConfiguration(configuration, context, aClass)) return;
        super.runForClass(aClass, psiMethod, context, performRunnable);
      }
    };
    if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, null, (PsiClass)fromContext.getSourceElement())) return;
    super.onFirstRun(fromContext, context, performRunnable);
  }

  private static boolean applyTestClassConfiguration(@NotNull ExternalSystemRunConfiguration configuration,
                                                     @NotNull ConfigurationContext context,
                                                     @NotNull PsiClass... containingClasses) {
    final Project project = context.getProject();
    final ExternalSystemTaskExecutionSettings settings = configuration.getSettings();
    if (!applyTestConfigurationFor(project, settings, containingClasses)) return false;
    configuration.setName(StringUtil.join(containingClasses, aClass -> aClass.getName(), "|"));
    return true;
  }

  @Deprecated
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
