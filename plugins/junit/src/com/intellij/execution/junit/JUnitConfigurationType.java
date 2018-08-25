// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JUnitConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  /**reflection*/
  public JUnitConfigurationType() {
    myFactory = new ConfigurationFactoryEx(this) {
      @Override
      @NotNull
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new JUnitConfiguration("", project, this);
      }

      @Override
      public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
        ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
      }
    };
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return ExecutionBundle.message("junit.configuration.display.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("junit.configuration.description");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Junit;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  @NotNull
  public String getId() {
    return "JUnit";
  }

  @NotNull
  @Override
  public String getConfigurationPropertyName() {
    String id = getId();
    return id.equals("JUnit") ? "junit" : id;
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  @NotNull
  public static JUnitConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
  }
}
