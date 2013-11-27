package com.intellij.remoteServer.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudGitDeploymentConfiguratorBase<D extends DeploymentConfiguration, S extends ServerConfiguration>
  extends DeploymentConfigurator<D, S> {

  private final Project myProject;
  private ServerType<S> myServerType;

  public CloudGitDeploymentConfiguratorBase(Project project, ServerType<S> serverType) {
    myProject = project;
    myServerType = serverType;
  }

  @Nullable
  public static CloudGitDeploymentSourceHandlerProvider getDeploymentSourceHandlerProvider(ServerType<?> serverType) {
    for (CloudGitDeploymentSourceHandlerProvider provider : CloudGitDeploymentSourceHandlerProvider.EP_NAME.getExtensions()) {
      if (provider.getServerType() == serverType) {
        return provider;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<DeploymentSource> getAvailableDeploymentSources() {
    if (myProject.isDefault()) return Collections.emptyList();
    List<DeploymentSource> result = new ArrayList<DeploymentSource>();
    ModulePointerManager pointerManager = ModulePointerManager.getInstance(myProject);
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      result.add(new ModuleDeploymentSourceImpl(pointerManager.create(module)));
    }
    CloudGitDeploymentSourceHandlerProvider provider = getDeploymentSourceHandlerProvider(myServerType);
    if (provider != null) {
      result.addAll(provider.getDeploymentSources(myProject));
    }
    return result;
  }
}
