// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import org.jetbrains.annotations.NotNull;

public abstract class JUnitConfigurationProducer extends AbstractJavaTestConfigurationProducer<JUnitConfiguration> implements Cloneable {
  public JUnitConfigurationProducer() {
    super();
  }

  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  protected JUnitConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
  }
}
