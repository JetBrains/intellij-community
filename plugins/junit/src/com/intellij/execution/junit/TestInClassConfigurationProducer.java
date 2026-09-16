// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TestInClassConfigurationProducer extends JUnitConfigurationProducer
  implements DumbAware {
  private final JUnitInClassConfigurationProducerDelegate myDelegate = new JUnitInClassConfigurationProducerDelegate();

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @Override
  public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                         @NotNull ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    myDelegate.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JUnitConfiguration configuration, @NotNull ConfigurationContext context) {
    String[] nodeIds = UniqueIdConfigurationProducer.getNodeIds(context);
    if (nodeIds != null && nodeIds.length > 0) return false;
    return super.isConfigurationFromContext(configuration, context);
  }

  @Override
  public @Nullable RunnerAndConfigurationSettings findExistingConfiguration(@NotNull ConfigurationContext context) {
    RunnerAndConfigurationSettings existing = super.findExistingConfiguration(context);
    if (existing == null) return null;
    if (existing.getConfiguration() instanceof JUnitConfiguration junitConfiguration &&
        JUnitParameterChooser.isOffered(junitConfiguration, contextElement(context))) {
      return null;
    }
    return existing;
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return myDelegate.isApplicableTestType(type, context);
  }

  private static @Nullable PsiMember contextElement(@NotNull ConfigurationContext context) {
    return ReadAction.computeBlocking(() -> {
      PsiElement element = context.getPsiLocation();
      return element != null && element.isValid() ? PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class) : null;
    });
  }

  private static class JUnitInClassConfigurationProducerDelegate extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
      return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    }

    @Override
    public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                           @NotNull ConfigurationContext fromContext,
                           @NotNull Runnable performRunnable) {
      // super asks which inheritor of an abstract test class to run, and the parameter of whatever it picked is chosen right before the run
      JUnitParameterChooser chooser = JUnitParameterChooser.of(configuration, fromContext, performRunnable);
      super.onFirstRun(configuration, fromContext, chooser == null ? performRunnable : chooser::chooseAndRun);
    }

    @Override
    protected boolean isApplicableTestType(String type, ConfigurationContext context) {
      return JUnitConfiguration.TEST_CLASS.equals(type) || JUnitConfiguration.TEST_METHOD.equals(type);
    }

    @Override
    protected boolean isRequiredVisibility(PsiMember psiElement) {
      if (JUnitUtil.isJUnit5(psiElement)) {
        return true;
      }
      return super.isRequiredVisibility(psiElement);
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
      return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
  }
}
