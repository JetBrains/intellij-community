/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
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
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Iterator;
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
  public void onFirstRun(@NotNull final ConfigurationFromContext fromContext, @NotNull final ConfigurationContext context, @NotNull final Runnable performRunnable) {
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
