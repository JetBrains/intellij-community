// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

//to be delete in 2018
@Deprecated
public class TestMethodConfigurationProducer extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  @SuppressWarnings("RedundantMethodOverride") // binary compatibility
  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    return super.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @SuppressWarnings("RedundantMethodOverride") // binary compatibility
  @Override
  public boolean isConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context) {
    return super.isConfigurationFromContext(configuration, context);
  }
}
