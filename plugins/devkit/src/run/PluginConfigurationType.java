/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.run;

import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class PluginConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;
  private String myVmParameters = "-Xmx512m -Xms256m -XX:MaxPermSize=250m -ea";

  public PluginConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        PluginRunConfiguration runConfiguration = new PluginRunConfiguration(project, this, "");
        if (runConfiguration.VM_PARAMETERS == null) {
          runConfiguration.VM_PARAMETERS = getVmParameters();
        }
        else {
          runConfiguration.VM_PARAMETERS += getVmParameters();
        }
        return runConfiguration;
      }

      @Override
      public boolean isApplicable(@NotNull Project project) {
        return ModuleUtil.hasModulesOfType(project, PluginModuleType.getInstance());
      }

      @Override
      public boolean isConfigurationSingletonByDefault() {
        return true;
      }

      @Override
      public RunConfiguration createConfiguration(String name, RunConfiguration template) {
        PluginRunConfiguration pluginRunConfiguration = (PluginRunConfiguration)template;
        if (pluginRunConfiguration.getModule() == null) {
          Collection<Module> modules = ModuleUtil.getModulesOfType(pluginRunConfiguration.getProject(), PluginModuleType.getInstance());
          pluginRunConfiguration.setModule(ContainerUtil.getFirstItem(modules));
        }
        return super.createConfiguration(name, pluginRunConfiguration);
      }
    };
  }

  @Override
  public String getDisplayName() {
    return DevKitBundle.message("run.configuration.title");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return DevKitBundle.message("run.configuration.type.description");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Plugin;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {myFactory};
  }

  @NotNull
  @Override
  public String getId() {
    return "#org.jetbrains.idea.devkit.run.PluginConfigurationType";
  }

  @NotNull
  private String getVmParameters() {
    if (myVmParameters == null) {
      String vmOptions;
      try {
        //noinspection SpellCheckingInspection
        vmOptions = FileUtil.loadFile(new File(PathManager.getBinPath(), "idea.plugins.vmoptions"));
      }
      catch (IOException e) {
        vmOptions = VMOptions.read();
      }
      myVmParameters = vmOptions != null ? vmOptions.replaceAll("\\s+", " ").trim() : "";
    }

    return myVmParameters;
  }
}
