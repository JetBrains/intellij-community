// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.filters;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ReRunTaskFilter extends GradleReRunBuildFilter {
  private final ExecutionEnvironment myEnv;

  public ReRunTaskFilter(ExternalSystemExecuteTaskTask task, ExecutionEnvironment env) {
    super(task.getExternalProjectPath());
    myEnv = env;
  }

  @Override
  protected @NotNull HyperlinkInfo getHyperLinkInfo(List<String> options) {
    return (project) -> {
      RunnerAndConfigurationSettings settings = myEnv.getRunnerAndConfigurationSettings();
      if (settings == null) return;
      RunConfiguration conf = settings.getConfiguration();
      if (!(conf instanceof ExternalSystemRunConfiguration)) return;

      ExternalSystemTaskExecutionSettings taskExecutionSettings = ((ExternalSystemRunConfiguration)conf).getSettings();
      String scriptParameters = taskExecutionSettings.getScriptParameters();
      List<String> params;
      if (StringUtil.isEmpty(scriptParameters)) {
        params = new SmartList<>();
      }
      else {
        params = new ArrayList<>(StringUtil.split(scriptParameters, " "));
        params.remove("--stacktrace");
        params.remove("--info");
        params.remove("--debug");
      }
      params.addAll(options);
      taskExecutionSettings.setScriptParameters(StringUtil.join(params, " "));

      ExecutionUtil.restart(myEnv);
    };
  }
}
