// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit;

import com.intellij.execution.actions.AbstractAddToTestsPatternAction;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class JUnitAddToTestsPatternAction extends AbstractAddToTestsPatternAction<JUnitConfiguration> {

  @Override
  protected @NotNull PatternConfigurationProducer getPatternBasedProducer() {
    return RunConfigurationProducer.getInstance(PatternConfigurationProducer.class);
  }

  @Override
  protected @NotNull ConfigurationType getConfigurationType() {
    return JUnitConfigurationType.getInstance();
  }

  @Override
  protected boolean isPatternBasedConfiguration(JUnitConfiguration configuration) {
    return JUnitConfiguration.TEST_PATTERN.equals(configuration.getPersistentData().TEST_OBJECT);
  }

  @Override
  protected Set<String> getPatterns(JUnitConfiguration configuration) {
    return configuration.getPersistentData().getPatterns();
  }
}