// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil;

import java.util.LinkedHashSet;
import java.util.List;

import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.applyTestConfiguration;

/**
 * @author Vladislav.Soroka
 */
public final class PatternGradleConfigurationProducer extends GradleTestRunConfigurationProducer {
  private final GradlePatternBasedConfigurationProducer myBaseConfigurationProducer = new GradlePatternBasedConfigurationProducer();

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

    final LinkedHashSet<String> tests = new LinkedHashSet<>();
    myBaseConfigurationProducer.checkPatterns(context, tests);
    if (tests.isEmpty()) {
      return false;
    }

    final Project project = context.getProject();
    final List<String> resolvedTests = ContainerUtil.newArrayList();
    final ExternalSystemTaskExecutionSettings settings = configuration.getSettings();
    final Function1<String, PsiClass> findPsiClass = (test) -> {
      final int i = StringUtil.indexOf(test, ",");
      String aClass = i < 0 ? test : test.substring(0, i);
      return JavaPsiFacade.getInstance(project).findClass(aClass, GlobalSearchScope.projectScope(project));
    };
    final Function2<PsiClass, String, String> createFilter = (psiClass, test) -> {
      final int i = StringUtil.indexOf(test, ",");
      final String method = i != -1 && test.length() > i + 1 ? test.substring(i + 1) : null;
      resolvedTests.add(psiClass.getName() + "," + (method == null ? "" : method));
      return GradleExecutionSettingsUtil.createTestFilterFrom(psiClass, /*hasSuffix=*/true);
    };
    if (!applyTestConfiguration(settings, project, tests, findPsiClass, createFilter)) {
      return false;
    }

    final String name;
    if (resolvedTests.size() > 1) {
      name = resolvedTests.get(0) + " and " + (resolvedTests.size() - 1) + " more";
    }
    else {
      name = StringUtil.join(tests, "|");
    }
    configuration.setName(name);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Override
  protected boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    return false;
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
    return myBaseConfigurationProducer.isMultipleElementsSelected(context);
  }

  private static class GradlePatternBasedConfigurationProducer extends AbstractPatternBasedConfigurationProducer {
    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
      return GradleExternalTaskConfigurationType.getInstance().getFactory();
    }

    @Override
    protected boolean setupConfigurationFromContext(RunConfiguration configuration, ConfigurationContext context, Ref sourceElement) {
      return false;
    }

    @Override
    public boolean isConfigurationFromContext(RunConfiguration configuration, ConfigurationContext context) {
      return false;
    }
  }
}
