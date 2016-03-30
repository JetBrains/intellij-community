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

import com.intellij.openapi.util.Computable;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.util.CloudAgent;
import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudRemoteApplication;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.Function;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudServerRuntimeInstance
  <DC extends DeploymentConfiguration,
    A extends CloudAgent,
    SC extends CloudAgentConfigBase>
  extends ServerRuntimeInstance<DC> {

  private final A myAgent;
  private final SC myConfiguration;
  private final ServerTaskExecutor myTasksExecutor;

  private final AgentTaskExecutor myAgentTaskExecutor;

  public CloudServerRuntimeInstance(SC configuration,
                                    ServerTaskExecutor tasksExecutor,
                                    List<File> libraries,
                                    List<Class<?>> commonJarClasses,
                                    String specificsModuleName,
                                    String specificJarPath,
                                    Class<A> agentInterface,
                                    String agentClassName) throws Exception {
    myConfiguration = configuration;
    myTasksExecutor = tasksExecutor;

    RemoteAgentManager agentManager = RemoteAgentManager.getInstance();
    myAgent = agentManager.createAgent(agentManager.createReflectiveThreadProxyFactory(getClass().getClassLoader()),
                                       libraries,
                                       commonJarClasses,
                                       specificsModuleName,
                                       specificJarPath,
                                       agentInterface,
                                       agentClassName,
                                       getClass());

    myAgentTaskExecutor = new AgentTaskExecutor();
  }

  public SC getConfiguration() {
    return myConfiguration;
  }

  public ServerTaskExecutor getTaskExecutor() {
    return myTasksExecutor;
  }

  public A getAgent() {
    return myAgent;
  }

  protected final AgentTaskExecutor getAgentTaskExecutor() {
    return myAgentTaskExecutor;
  }

  @Override
  public void computeDeployments(@NotNull final ComputeDeploymentsCallback callback) {
    getTaskExecutor().submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        try {
          for (CloudApplicationRuntime application : getApplications()) {
            Deployment deployment
              = callback.addDeployment(application.getApplicationName(),
                                       application,
                                       application.getStatus(),
                                       application.getStatusText());
            application.setDeploymentModel(deployment);
          }
          callback.succeeded();
        }
        catch (ServerRuntimeException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    }, callback);
  }

  protected List<CloudApplicationRuntime> getApplications() throws ServerRuntimeException {
    return getAgentTaskExecutor().execute(new Computable<List<CloudApplicationRuntime>>() {

      @Override
      public List<CloudApplicationRuntime> compute() {
        CloudRemoteApplication[] applications = getAgent().getApplications();
        if (applications == null) {
          return Collections.emptyList();
        }
        return ContainerUtil.map(applications, new Function<CloudRemoteApplication, CloudApplicationRuntime>() {
          @Override
          public CloudApplicationRuntime fun(CloudRemoteApplication application) {
            return createApplicationRuntime(application);
          }
        });
      }
    });
  }

  protected abstract CloudApplicationRuntime createApplicationRuntime(CloudRemoteApplication application);
}
