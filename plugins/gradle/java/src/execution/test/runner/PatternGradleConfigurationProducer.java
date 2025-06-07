// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;

import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.applyTestConfiguration;
import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.getSourceFile;
import static org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom;


public final class PatternGradleConfigurationProducer extends GradleTestRunConfigurationProducer {
  private final GradlePatternBasedConfigurationProducer<?> myBaseConfigurationProducer = new GradlePatternBasedConfigurationProducer<>();

  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return other.isProducedBy(TestClassGradleConfigurationProducer.class) ||
           other.isProducedBy(TestMethodGradleConfigurationProducer.class) ||
           super.isPreferredConfiguration(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return other.isProducedBy(TestClassGradleConfigurationProducer.class) ||
           other.isProducedBy(TestMethodGradleConfigurationProducer.class) ||
           super.shouldReplace(self, other);
  }

  @Override
  protected boolean doSetupConfigurationFromContext(
    @NotNull GradleRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement
  ) {
    if (!myBaseConfigurationProducer.isMultipleElementsSelected(context)) return false;
    ExternalSystemTaskExecutionSettings settings = configuration.getSettings();
    if (!GradleConstants.SYSTEM_ID.equals(settings.getExternalSystemId())) return false;
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    Project project = context.getProject();
    List<String> tests = getTestPatterns(context);
    if (tests.isEmpty()) return false;
    TestMappings testMappings = getTestMappings(project, tests);
    Function1<String, VirtualFile> findTestSource = test -> getSourceFile(testMappings.getClasses().get(test));
    Function1<String, String> createFilter = (test) ->
      createTestFilterFrom(testMappings.getClasses().get(test), testMappings.getMethods().get(test));
    Module module = getModuleFromContext(context);
    if (module == null) return false;
    if (!applyTestConfiguration(settings, module, tests, findTestSource, createFilter)) return false;
    configuration.setName(suggestConfigurationName(tests));
    setUniqueNameIfNeeded(project, configuration);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Override
  protected boolean doIsConfigurationFromContext(
    @NotNull GradleRunConfiguration configuration,
    @NotNull ConfigurationContext context
  ) {
    return false;
  }

  @Override
  public void onFirstRun(
    @NotNull ConfigurationFromContext configuration,
    @NotNull ConfigurationContext context,
    @NotNull Runnable startRunnable
  ) {
    if (!myBaseConfigurationProducer.isMultipleElementsSelected(context)) {
      super.onFirstRun(configuration, context, startRunnable);
      return;
    }
    Module module = getModuleFromContext(context);
    if (module == null) {
      LOG.warn("Cannot find module from context, uses raw run configuration");
      super.onFirstRun(configuration, context, startRunnable);
      return;
    }
    GradleRunConfiguration runConfiguration = (GradleRunConfiguration)configuration.getConfiguration();
    Project project = context.getProject();
    List<String> tests = getTestPatterns(context);
    if (tests.isEmpty()) {
      LOG.warn("Cannot find runnable tests from context, uses raw run configuration");
      super.onFirstRun(configuration, context, startRunnable);
      return;
    }
    TestMappings testMappings = getTestMappings(project, tests);
    getTestTasksChooser().chooseTestTasks(project, context.getDataContext(), testMappings.getClasses().values(), tasks -> {
      ExternalSystemTaskExecutionSettings settings = runConfiguration.getSettings();
      Function1<String, VirtualFile> findTestSource = test -> getSourceFile(testMappings.getClasses().get(test));
      Function1<String, String> createFilter = (test) ->
        createTestFilterFrom(testMappings.getClasses().get(test), testMappings.getMethods().get(test));
      if (!applyTestConfiguration(settings, module, tasks, tests, findTestSource, createFilter)) {
        LOG.warn("Cannot apply pattern test configuration, uses raw run configuration");
        super.onFirstRun(configuration, context, startRunnable);
        return;
      }
      runConfiguration.setName(suggestConfigurationName(tests));
      setUniqueNameIfNeeded(project, runConfiguration);
      super.onFirstRun(configuration, context, startRunnable);
    });
  }

  private static @NotNull String suggestConfigurationName(List<String> tests) {
    if (tests.isEmpty()) return "";
    if (tests.size() == 1) return tests.get(0);
    return GradleBundle.message("gradle.tests.pattern.producer.configuration.name", tests.get(0), tests.size() - 1);
  }

  private static @Nullable Module getModuleFromContext(ConfigurationContext context) {
    Module module = context.getModule();
    if (module != null) return module;
    Location contextLocation = context.getLocation();
    if (contextLocation == null) return null;
    PsiElement locationElement = contextLocation.getPsiElement();
    return ModuleUtilCore.findModuleForPsiElement(locationElement);
  }

  private static @NotNull TestMappings getTestMappings(@NotNull Project project, @NotNull List<String> tests) {
    Map<String, PsiClass> classes = new LinkedHashMap<>();
    Map<String, String> methods = new LinkedHashMap<>();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    for (String test : tests) {
      int i = StringUtil.indexOf(test, ",");
      String className = i < 0 ? test : test.substring(0, i);
      PsiClass psiClass = psiFacade.findClass(className, projectScope);
      classes.put(test, psiClass);
      String method = i != -1 && test.length() > i + 1 ? test.substring(i + 1) : null;
      methods.put(test, method);
    }
    return new TestMappings(classes, methods);
  }

  private List<String> getTestPatterns(@NotNull ConfigurationContext context) {
    LinkedHashSet<String> tests = new LinkedHashSet<>();
    myBaseConfigurationProducer.checkPatterns(context, tests);
    return new ArrayList<>(tests);
  }

  private static final class TestMappings {
    private final Map<String, PsiClass> classes;
    private final Map<String, String> methods;

    private TestMappings(Map<String, PsiClass> classes, Map<String, String> methods) {
      this.classes = classes;
      this.methods = methods;
    }

    private Map<String, PsiClass> getClasses() {
      return classes;
    }

    private Map<String, String> getMethods() {
      return methods;
    }
  }

  private static class GradlePatternBasedConfigurationProducer<T extends JavaTestConfigurationBase>
    extends AbstractPatternBasedConfigurationProducer<T> {
    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
      return GradleExternalTaskConfigurationType.getInstance().getFactory();
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull T configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
      return false;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull T configuration, @NotNull ConfigurationContext context) {
      return false;
    }

    @Override
    public void collectTestMembers(PsiElement[] psiElements,
                                   boolean checkAbstract,
                                   boolean checkIsTest,
                                   PsiElementProcessor.CollectElements<PsiElement> collectingProcessor) {
      PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>();
      super.collectTestMembers(psiElements, false, false, processor);
      // It is needed because checks on isTest makes by the test framework that founds by the configuration type
      // But GradleExternalTaskConfigurationType is not supported by any test framework
      for (PsiElement element : processor.getCollection()) {
        if (!checkIsTest || isTest(element, checkAbstract)) {
          collectingProcessor.execute(element);
        }
      }
    }

    private static boolean isTest(PsiElement element, boolean checkAbstract) {
      return element instanceof PsiClass && isTestClass((PsiClass)element, checkAbstract) ||
             element instanceof PsiMethod && isTestMethod((PsiMethod)element, checkAbstract);
    }

    private static boolean isTestMethod(PsiMethod psiMethod, boolean checkAbstract) {
      return TestFrameworks.getInstance().isTestMethod(psiMethod, checkAbstract);
    }

    private static boolean isTestClass(PsiClass psiMethod, boolean checkAbstract) {
      if (checkAbstract && psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
      return TestFrameworks.getInstance().isTestClass(psiMethod);
    }
  }
}
