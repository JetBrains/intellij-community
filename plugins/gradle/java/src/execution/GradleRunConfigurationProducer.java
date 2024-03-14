// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

public abstract class GradleRunConfigurationProducer extends LazyRunConfigurationProducer<GradleRunConfiguration> {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
  }
}
