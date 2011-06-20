/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GriffonRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myConfigurationFactory;
  @NonNls private static final String GRIFFON_APPLICATION = "Griffon";

  public GriffonRunConfigurationType() {
    myConfigurationFactory = new ConfigurationFactory(this) {
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new GriffonRunConfiguration(this, project, GRIFFON_APPLICATION, "run-app");
      }
    };
  }

  public String getDisplayName() {
    return GRIFFON_APPLICATION;
  }

  public String getConfigurationTypeDescription() {
    return GRIFFON_APPLICATION;
  }

  public Icon getIcon() {
    return GriffonFramework.GRIFFON_ICON;
  }

  @NonNls
  @NotNull
  public String getId() {
    return "GriffonRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  public static GriffonRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GriffonRunConfigurationType.class);
  }

}