// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.run;

import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public final class PluginConfigurationType extends SimpleConfigurationType {
  private String myVmParameters = "-Xmx512m -Xms256m -ea";

  public PluginConfigurationType() {
    super("#org.jetbrains.idea.devkit.run.PluginConfigurationType", DevKitBundle.message("run.configuration.title"), DevKitBundle.message("run.configuration.type.description"),
          NotNullLazyValue.createValue(() -> AllIcons.Nodes.Plugin));
  }

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

  @NotNull
  @Override
  public RunConfiguration createConfiguration(@Nullable String name, @NotNull RunConfiguration template) {
    PluginRunConfiguration pluginRunConfiguration = (PluginRunConfiguration)template;
    if (pluginRunConfiguration.getModule() == null) {
      Collection<Module> modules = ModuleUtil.getModulesOfType(pluginRunConfiguration.getProject(), PluginModuleType.getInstance());
      pluginRunConfiguration.setModule(ContainerUtil.getFirstItem(modules));
    }
    return super.createConfiguration(name, pluginRunConfiguration);
  }

  @NotNull
  @Override
  public String getTag() {
    return "plugin";
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.devkit.PluginConfigurationType";
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

  @NotNull
  @Override
  public RunConfigurationSingletonPolicy getSingletonPolicy() {
    return RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY;
  }
}
