/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.appengine.actions;

import com.intellij.appengine.cloud.AppEngineCloudType;
import com.intellij.appengine.cloud.AppEngineServerConfiguration;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class UploadApplicationAction extends AnAction {
  public static final String LAST_RUN_CONFIGURATION_PROPERTY = "JAVA_APP_ENGINE_LAST_RUN_CONFIGURATION";

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(
      project != null && ProjectFacetManager.getInstance(project).hasFacets(AppEngineFacet.ID));

    if (project != null) {
      String text;
      RunnerAndConfigurationSettings configurationToRun = getConfigurationToRun(project);
      if (configurationToRun == null) {
        text = getTemplatePresentation().getText();
      }
      else {
        text = "Upload App Engine Application '" + configurationToRun.getName() + "'";
      }
      e.getPresentation().setText(text);
    }
  }

  @Nullable
  private static RunnerAndConfigurationSettings getConfigurationToRun(@NotNull Project project) {
    List<RunnerAndConfigurationSettings> configurations = DeploymentConfigurationManager.getInstance(project).getDeploymentConfigurations(AppEngineCloudType.getInstance());
    String lastName = PropertiesComponent.getInstance(project).getValue(LAST_RUN_CONFIGURATION_PROPERTY);
    if (lastName != null) {
      for (RunnerAndConfigurationSettings configuration : configurations) {
        if (configuration.getName().equals(lastName)) {
          return configuration;
        }
      }
    }

    return ContainerUtil.getFirstItem(configurations);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    RunnerAndConfigurationSettings configurationToRun = getConfigurationToRun(project);
    if (configurationToRun != null) {
      ProgramRunnerUtil.executeConfiguration(configurationToRun, DefaultRunExecutor.getRunExecutorInstance());
    }
    else {
      AppEngineCloudType serverType = AppEngineCloudType.getInstance();
      List<RemoteServer<AppEngineServerConfiguration>> servers = RemoteServersManager.getInstance().getServers(serverType);
      DeploymentConfigurationManager.getInstance(project).createAndRunConfiguration(serverType, ContainerUtil.getFirstItem(servers));
    }
  }
}
