// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.PsiUtil;

public class DevKitApplicationPatcher extends RunConfigurationExtension {
  @Override
  public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration,
                                                                       @NotNull JavaParameters javaParameters,
                                                                       RunnerSettings runnerSettings) {
    ParametersList vmParametersList = javaParameters.getVMParametersList();
    Project project = configuration.getProject();
    if (PsiUtil.isIdeaProject(project) && 
        !vmParametersList.getList().contains("--add-modules") && 
        "com.intellij.idea.Main".equals(((ApplicationConfiguration)configuration).getMainClassName())) {
      Module module = ((ApplicationConfiguration)configuration).getConfigurationModule().getModule();

      Sdk jdk = JavaParameters.getJdkToRunModule(module, true);
      if (jdk == null) {
        return;
      }

      if (!vmParametersList.hasProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY)) {
        String qualifiedName = "com.intellij.util.lang.PathClassLoader";
        if (JUnitDevKitPatcher.loaderValid(project, module, qualifiedName)) {
          vmParametersList.addProperty (JUnitDevKitPatcher.SYSTEM_CL_PROPERTY, qualifiedName);
        }
      }

      if (!vmParametersList.getList().contains("--add-opens")) {
        JUnitDevKitPatcher.appendAddOpensWhenNeeded(project, jdk, vmParametersList);
      }
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
    return configuration instanceof ApplicationConfiguration;
  }
}
