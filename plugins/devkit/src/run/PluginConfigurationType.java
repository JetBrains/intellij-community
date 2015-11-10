/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  private String myVmParameters;

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
