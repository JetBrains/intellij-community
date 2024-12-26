// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import org.jetbrains.annotations.NotNull;

public abstract class JUnitConfigurationProducer extends AbstractJavaTestConfigurationProducer<JUnitConfiguration> implements Cloneable {
  public JUnitConfigurationProducer() {
    super();
  }

  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
  }
}
