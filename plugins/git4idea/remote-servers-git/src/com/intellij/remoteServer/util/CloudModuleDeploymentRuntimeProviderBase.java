/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudModuleDeploymentRuntimeProviderBase implements CloudDeploymentRuntimeProvider {

  private static final Logger LOG = Logger.getInstance("#" + CloudModuleDeploymentRuntimeProviderBase.class.getName());

  @Override
  public Collection<DeploymentSource> getDeploymentSources(Project project) {
    List<DeploymentSource> result = new ArrayList<>();
    ModulePointerManager pointerManager = ModulePointerManager.getInstance(project);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      result.add(new ModuleDeploymentSourceImpl(pointerManager.create(module)));
    }
    return result;
  }

  @Override
  public CloudDeploymentRuntime createDeploymentRuntime(DeploymentSource source,
                                                        CloudMultiSourceServerRuntimeInstance serverRuntime,
                                                        DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                        DeploymentLogManager logManager)
    throws ServerRuntimeException {
    if (!(source instanceof ModuleDeploymentSource)) {
      return null;
    }

    ModuleDeploymentSource moduleSource = (ModuleDeploymentSource)source;
    Module module = moduleSource.getModule();
    if (module == null) {
      throw new ServerRuntimeException("Module not found: " + moduleSource.getModulePointer().getModuleName());
    }

    File contentRootFile = source.getFile();
    LOG.assertTrue(contentRootFile != null, "Content root file is not found");

    return doCreateDeploymentRuntime(moduleSource, contentRootFile, serverRuntime, deploymentTask, logManager);
  }

  protected abstract CloudDeploymentRuntime doCreateDeploymentRuntime(ModuleDeploymentSource moduleSource,
                                                                      File contentRootFile,
                                                                      CloudMultiSourceServerRuntimeInstance runtime,
                                                                      DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                                      DeploymentLogManager logManager) throws ServerRuntimeException;
}
