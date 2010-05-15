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

package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;

public class GroovyScriptRunConfigurationType implements ConfigurationType {
  private final GroovyFactory myConfigurationFactory;

  public GroovyScriptRunConfigurationType() {
    myConfigurationFactory = new GroovyFactory(this);
  }

  public String getDisplayName() {
    return "Groovy Script";
  }

  public String getConfigurationTypeDescription() {
    return "Groovy Script";
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NonNls
  @NotNull
  public String getId() {
    return "GroovyScriptRunConfiguration";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  public static GroovyScriptRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GroovyScriptRunConfigurationType.class);
  }

  public static class GroovyFactory extends ConfigurationFactory {
    public GroovyFactory(ConfigurationType type) {
      super(type);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new GroovyScriptRunConfiguration("Groovy Script", project, this);
    }

  }
}
