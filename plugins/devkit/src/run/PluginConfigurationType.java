/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class PluginConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;
  private String myVmParameters;

  PluginConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        final PluginRunConfiguration runConfiguration = new PluginRunConfiguration(project, this, "");
        if (runConfiguration.VM_PARAMETERS == null) {
          runConfiguration.VM_PARAMETERS = getVmParameters();
        } else {
          runConfiguration.VM_PARAMETERS += getVmParameters();
        }
        return runConfiguration;
      }

      public RunConfiguration createConfiguration(String name, RunConfiguration template) {
        final PluginRunConfiguration pluginRunConfiguration = (PluginRunConfiguration)template;
        if (pluginRunConfiguration.getModule() == null) {
          final Module[] modules = pluginRunConfiguration.getModules();
          if (modules != null && modules.length > 0){
            pluginRunConfiguration.setModule(modules[0]);
          }
        }
        return super.createConfiguration(name, pluginRunConfiguration);
      }
    };
  }
  private static final Icon ICON = IconLoader.getIcon("/nodes/plugin.png");

  public String getDisplayName() {
    return DevKitBundle.message("run.configuration.title");
  }

  public String getConfigurationTypeDescription() {
    return DevKitBundle.message("run.configuration.type.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {myFactory};
  }

  public String getComponentName() {
    return "#org.jetbrains.idea.devkit.run.PluginConfigurationType";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getVmParameters() {
    if (myVmParameters == null) {
      myVmParameters = "";

      String vmParameters = readFile("idea.plugins.vmoptions");
      if (vmParameters != null) {
        myVmParameters = vmParameters;
      } else if ((vmParameters = readFile("idea.exe.vmoptions")) != null) {
        myVmParameters = vmParameters;
      }
    }
    return myVmParameters;
  }

  @Nullable
  private static String readFile(@NonNls String fileName) {
    final File file = new File(PathManager.getBinPath(), fileName);
    if (file.exists()) {
      final StringBuffer lines = new StringBuffer();
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
          lines.append(" ").append(line);
        }
      }
      catch (IOException e) {
        //skip
      }
      finally {
        if (reader != null) {
          try {
            reader.close();
          }
          catch (IOException e) {
            //skip
          }
        }
      }
      return lines.toString();
    }
    return null;
  }
}