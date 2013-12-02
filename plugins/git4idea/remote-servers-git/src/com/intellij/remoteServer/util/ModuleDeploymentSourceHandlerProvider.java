package com.intellij.remoteServer.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author michael.golubev
 */
public class ModuleDeploymentSourceHandlerProvider implements CloudGitDeploymentSourceHandlerProvider {

  @Nullable
  @Override
  public ServerType<?> getServerType() {
    return null;
  }

  @Override
  public Collection<DeploymentSource> getDeploymentSources(Project project) {
    List<DeploymentSource> result = new ArrayList<DeploymentSource>();
    ModulePointerManager pointerManager = ModulePointerManager.getInstance(project);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      result.add(new ModuleDeploymentSourceImpl(pointerManager.create(module)));
    }
    return result;
  }

  @Override
  public DeploymentSourceHandler createHandler(CloudGitDeploymentRuntime<?, ?, ?> deploymentRuntime,
                                               DeploymentSource deploymentSource,
                                               DeploymentConfiguration deploymentConfiguration)
    throws ServerRuntimeException {
    if (!(deploymentSource instanceof ModuleDeploymentSource)) {
      return null;
    }
    return deploymentRuntime.new ModuleDeploymentSourceHandler((ModuleDeploymentSource)deploymentSource);
  }
}
