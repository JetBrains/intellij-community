// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;

final class GradleRuntimeConfigurationProducer extends AbstractExternalSystemRunConfigurationProducer {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
  }
}
