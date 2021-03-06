// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;

public final class GroovyScriptRunConfigurationType implements ConfigurationType {
  private final GroovyFactory myConfigurationFactory;

  public GroovyScriptRunConfigurationType() {
    myConfigurationFactory = new GroovyFactory(this);
  }

  @NotNull
  @Override
  public String getDisplayName() {
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
  @NonNls
  @NotNull
  public String getId() {
    return "GroovyScriptRunConfiguration";
  }

  @NotNull
  public ConfigurationFactory getConfigurationFactory() {
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
    public boolean isApplicable(@NotNull Project project) {
      return FileTypeIndex.containsFileOfType(GroovyFileType.GROOVY_FILE_TYPE, GlobalSearchScope.allScope(project));
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new GroovyScriptRunConfiguration("Groovy Script", project, this);
    }
  }
}
