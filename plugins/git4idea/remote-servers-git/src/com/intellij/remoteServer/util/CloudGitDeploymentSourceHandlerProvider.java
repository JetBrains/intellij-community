package com.intellij.remoteServer.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author michael.golubev
 */
public interface CloudGitDeploymentSourceHandlerProvider {

  ExtensionPointName<CloudGitDeploymentSourceHandlerProvider> EP_NAME
    = ExtensionPointName.create("Git4Idea.remoteServer.CloudGitDeploymentSourceHandlerProvider");

  @Nullable
  ServerType<?> getServerType();

  Collection<DeploymentSource> getDeploymentSources(Project project);

  DeploymentSourceHandler createHandler(CloudGitDeploymentRuntime<?, ?, ?> deploymentRuntime,
                                        DeploymentSource deploymentSource,
                                        DeploymentConfiguration deploymentConfiguration)
    throws ServerRuntimeException;
}
