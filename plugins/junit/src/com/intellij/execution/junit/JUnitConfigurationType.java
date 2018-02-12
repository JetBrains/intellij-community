/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  public String getDisplayName() {
    return ExecutionBundle.message("junit.configuration.display.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("junit.configuration.description");
  }

  public Icon getIcon() {
    return AllIcons.RunConfigurations.Junit;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NotNull
  public String getId() {
    return "JUnit";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @NotNull
  public static JUnitConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
  }
}
