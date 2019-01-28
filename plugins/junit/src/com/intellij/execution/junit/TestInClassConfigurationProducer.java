// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;

public final class TestInClassConfigurationProducer extends JUnitConfigurationProducer {
  private final JUnitInClassConfigurationProducerDelegate myDelegate = new JUnitInClassConfigurationProducerDelegate();

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @Override
  public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                         @NotNull ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    myDelegate.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  public boolean isConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context) {
    String[] nodeIds = UniqueIdConfigurationProducer.getNodeIds(context);
    if (nodeIds != null && nodeIds.length > 0) return false;
    return super.isConfigurationFromContext(configuration, context);
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return myDelegate.isApplicableTestType(type, context);
  }

  private static class JUnitInClassConfigurationProducerDelegate extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
      return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
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
    protected boolean setupConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
      return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
  }
}
