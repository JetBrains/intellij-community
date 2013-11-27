package com.intellij.remoteServer.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;

import java.util.Collection;
import java.util.List;

/**
 * @author michael.golubev
 */
public interface CloudGitDeploymentSourceHandlerProvider {

  ExtensionPointName<CloudGitDeploymentSourceHandlerProvider> EP_NAME
    = ExtensionPointName.create("Git4Idea.remoteServer.CloudGitDeploymentSourceHandlerProvider");

  ServerType<?> getServerType();

  Collection<DeploymentSource> getDeploymentSources(Project project);

  List<DeploymentSourceHandler> getHandlers(CloudGitDeploymentRuntime<?, ?, ?> deploymentRuntime);
}
