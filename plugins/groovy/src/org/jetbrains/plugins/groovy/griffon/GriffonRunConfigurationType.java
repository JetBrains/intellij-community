// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class GriffonRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myConfigurationFactory;
  @NonNls private static final String GRIFFON_APPLICATION = "Griffon";

  public GriffonRunConfigurationType() {
    myConfigurationFactory = new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new GriffonRunConfiguration(this, project, GRIFFON_APPLICATION, "run-app");
      }

      @Override
      public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
        if (providerID == CompileStepBeforeRun.ID || providerID == CompileStepBeforeRunNoErrorCheck.ID) {
          task.setEnabled(false);
        }
      }
    };
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return GRIFFON_APPLICATION;
  }

  @Override
  public String getConfigurationTypeDescription() {
    return GRIFFON_APPLICATION;
  }

  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Griffon.Griffon;
  }

  @Override
  @NonNls
  @NotNull
  public String getId() {
    return "GriffonRunConfigurationType";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  public static GriffonRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GriffonRunConfigurationType.class);
  }

}
