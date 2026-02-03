// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

public final class AntRunConfigurationType extends SimpleConfigurationType {
  public AntRunConfigurationType() {
    super("AntRunConfiguration", AntBundle.message("configuration.type.name.ant.target"),
          AntBundle.message("configuration.type.description.run.ant.target"), NotNullLazyValue.lazy(() -> AntIcons.Build));
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.AntRunConfiguration";
  }

  public static @NotNull AntRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AntRunConfigurationType.class);
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new AntRunConfiguration(project, this);
  }
}
