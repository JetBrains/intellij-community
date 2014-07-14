/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.agent.util.CloudGitApplication;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public class CloudGitDeploymentChecker<
  T extends CloudDeploymentNameConfiguration,
  SC extends ServerConfigurationBase,
  SR extends CloudMultiSourceServerRuntimeInstance<T, ?, ?, ?>> {

  private final CloudGitDeploymentDetector myDetector;

  public CloudGitDeploymentChecker(CloudGitDeploymentDetector detector) {
    myDetector = detector;
  }

  public void checkGitUrl(final RemoteServer<SC> server,
                          final DeploymentSource deploymentSource,
                          final T settings) throws RuntimeConfigurationException {
    if (!(deploymentSource instanceof ModuleDeploymentSource)) {
      return;
    }

    ModuleDeploymentSource moduleSource = (ModuleDeploymentSource)deploymentSource;
    Module module = moduleSource.getModule();
    if (module == null) {
      return;
    }

    File contentRootFile = deploymentSource.getFile();
    if (contentRootFile == null) {
      return;
    }

    final Project project = module.getProject();

    VirtualFile contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(contentRootFile);
    if (contentRoot == null) {
      return;
    }

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(contentRoot);
    if (repository == null) {
      return;
    }


    String expectedName = settings.getDeploymentSourceName(deploymentSource);

    List<String> appNames = myDetector.collectApplicationNames(repository);
    if (appNames.isEmpty() || appNames.contains(expectedName)) {
      return;
    }

    RuntimeConfigurationWarning warning =
      new RuntimeConfigurationWarning("Cloud Git URL found in repository, but it doesn't match the run configuration");

    warning.setQuickFix(new Runnable() {

      @Override
      public void run() {
        CloudGitApplication application
          = new CloudConnectionTask<CloudGitApplication, SC, T, SR>(project, "Searching for application", server) {

          @Override
          protected CloudGitApplication run(SR serverRuntime) throws ServerRuntimeException {
            CloudGitDeploymentRuntime deploymentRuntime
              = (CloudGitDeploymentRuntime)serverRuntime.createDeploymentRuntime(deploymentSource, settings, project);
            return deploymentRuntime.findApplication4Repository();
          }
        }.performSync();

        if (application == null) {
          Messages.showErrorDialog(project, "No application matching repository URL(s) found in account", server.getName());
        }
        else {
          settings.setDefaultDeploymentName(false);
          settings.setDeploymentName(application.getName());
        }
      }
    });

    throw warning;
  }
}
