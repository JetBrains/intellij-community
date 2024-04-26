// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

public abstract class GradleRunConfigurationProducer extends LazyRunConfigurationProducer<GradleRunConfiguration> {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
  }

  @Override
  public abstract boolean setupConfigurationFromContext(@NotNull GradleRunConfiguration configuration,
                                                        @NotNull ConfigurationContext context,
                                                        @NotNull Ref<PsiElement> sourceElement);
}
