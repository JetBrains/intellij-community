// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.LazyUtil;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

public final class AntRunConfigurationType extends ConfigurationTypeBase {
  public AntRunConfigurationType() {
    super("AntRunConfiguration", "Ant Target", "Run Ant Target", LazyUtil.create(() -> AntIcons.Build));
    addFactory(new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new AntRunConfiguration(project, this, "");
      }
    });
  }

  public static AntRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AntRunConfigurationType.class);
  }

  public ConfigurationFactory getFactory() {
    return getConfigurationFactories()[0];
  }
}
