// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchRunConfigurationType implements ConfigurationType {

  @Nls
  @Override
  public String getDisplayName() {
    return "Structural Search";
  }

  @Nls
  @Override
  public String getConfigurationTypeDescription() {
    return getDisplayName();
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Find;
  }

  @NotNull
  @Override
  public String getId() {
    return "SSR_DSL_RUN_CONFIGURATION";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {
      new StructuralSearchConfigurationFactory(this)
    };
  }
}
