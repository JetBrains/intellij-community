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

import com.intellij.remoteServer.agent.util.CloudAgentApplication;
import com.intellij.remoteServer.agent.util.CloudAgentBase;
import com.intellij.remoteServer.agent.util.CloudRemoteApplication;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;

public class CloudApplicationRuntimeImpl extends CloudApplicationRuntimeBase {

  private final CloudAgentApplication myAgentApplication;

  public CloudApplicationRuntimeImpl(ServerTaskExecutor taskExecutor,
                                     CloudAgentBase<?, CloudRemoteApplication> agent,
                                     CloudRemoteApplication applicationIdentity) {
    super(taskExecutor, applicationIdentity.getName());
    myAgentApplication = agent.createApplication(applicationIdentity);
  }

  @Override
  protected CloudAgentApplication getApplication() {
    return myAgentApplication;
  }
}
