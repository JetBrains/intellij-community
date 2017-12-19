/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AntRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new ConfigurationFactory(this) {
    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new AntRunConfiguration(project, this, "");
    }
  };

  public static AntRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AntRunConfigurationType.class);
  }


  @Override
  public String getDisplayName() {
    return "Ant Target";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Run Ant Target";
  }

  @Override
  public Icon getIcon() {
    return AntIcons.Build;
  }

  @NotNull
  @Override
  public String getId() {
    return "AntRunConfiguration";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}
