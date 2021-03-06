// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// cannot be final because of backward compatibility (1 external usage)
/**
 * DO NOT extend this class directly.
 */
public class JUnitConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  /**reflection*/
  public JUnitConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      @Override
      @NotNull
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new JUnitConfiguration("", project, this);
      }

      @Override
      public @NotNull String getId() {
        return "JUnit";
      }

      @Override
      public boolean isEditableInDumbMode() {
        return true;
      }
    };
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return JUnitBundle.message("junit.configuration.display.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return JUnitBundle.message("junit.configuration.description");
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
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.JUnit";
  }

  @Override
  @NotNull
  public String getId() {
    return "JUnit";
  }

  @NotNull
  @Override
  public String getTag() {
    String id = getId();
    return id.equals("JUnit") ? "junit" : id;
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
