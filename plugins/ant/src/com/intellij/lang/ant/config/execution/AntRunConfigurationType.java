// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

public final class AntRunConfigurationType extends SimpleConfigurationType {
  public AntRunConfigurationType() {
    super("AntRunConfiguration", "Ant Target", "Run Ant Target", NotNullLazyValue.createValue(() -> AntIcons.Build));
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.AntRunConfiguration";
  }

  @NotNull
  public static AntRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AntRunConfigurationType.class);
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new AntRunConfiguration(project, this);
  }
}
