package com.intellij.remoteServer.util;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudDeploymentConfiguratorBase<D extends DeploymentConfiguration, S extends ServerConfiguration>
  extends DeploymentConfigurator<D, S> {

  private final Project myProject;
  private ServerType<S> myServerType;

  public CloudDeploymentConfiguratorBase(Project project, ServerType<S> serverType) {
    myProject = project;
    myServerType = serverType;
  }

  public static List<CloudDeploymentRuntimeProvider> getDeploymentRuntimeProviders(ServerType<?> serverType) {
    List<CloudDeploymentRuntimeProvider> result = new ArrayList<>();
    for (CloudDeploymentRuntimeProvider provider : CloudDeploymentRuntimeProvider.EP_NAME.getExtensions()) {
      ServerType<?> providerServerType = provider.getServerType();
      if (providerServerType == null || providerServerType == serverType) {
        result.add(provider);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public List<DeploymentSource> getAvailableDeploymentSources() {
    if (myProject.isDefault()) return Collections.emptyList();
    List<DeploymentSource> result = new ArrayList<>();
    for (CloudDeploymentRuntimeProvider provider : getDeploymentRuntimeProviders(myServerType)) {
      result.addAll(provider.getDeploymentSources(myProject));
    }
    return result;
  }
}
