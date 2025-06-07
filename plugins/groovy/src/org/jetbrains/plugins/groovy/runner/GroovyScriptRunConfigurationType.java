// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;

public final class GroovyScriptRunConfigurationType implements ConfigurationType {
  private final GroovyFactory myConfigurationFactory;

  public GroovyScriptRunConfigurationType() {
    myConfigurationFactory = new GroovyFactory(this);
  }

  @Override
  public @NotNull String getDisplayName() {
    return GroovyBundle.message("script.runner.display.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return GroovyBundle.message("script.runner.description");
  }

  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public @NonNls @NotNull String getId() {
    return "GroovyScriptRunConfiguration";
  }

  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.GroovyScriptRunConfiguration";
  }

  public static GroovyScriptRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GroovyScriptRunConfigurationType.class);
  }

  private static class GroovyFactory extends ConfigurationFactory {
    GroovyFactory(ConfigurationType type) {
      super(type);
    }

    @Override
    public @NotNull String getId() {
      return "Groovy";
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new GroovyScriptRunConfiguration("Groovy Script", project, this);
    }
  }
}
