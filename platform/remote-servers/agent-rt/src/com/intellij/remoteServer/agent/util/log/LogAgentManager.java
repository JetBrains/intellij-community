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
package com.intellij.remoteServer.agent.util.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author michael.golubev
 */
public class LogAgentManager {

  private Map<String, List<LogPipeBase>> myDeploymentName2ActiveLogPipes = new HashMap<String, List<LogPipeBase>>();

  public void startListeningLog(String deploymentName, LogPipeProvider provider) {
    stopListeningLog(deploymentName);
    doStartListeningLog(deploymentName, provider);
  }

  public void startOrContinueListeningLog(String deploymentName, LogPipeProvider provider) {
    if (!myDeploymentName2ActiveLogPipes.containsKey(deploymentName)) {
      doStartListeningLog(deploymentName, provider);
    }
  }

  private void doStartListeningLog(String deploymentName, LogPipeProvider provider) {
    ArrayList<LogPipeBase> pipes = new ArrayList<LogPipeBase>(provider.createLogPipes(deploymentName));
    myDeploymentName2ActiveLogPipes.put(deploymentName, pipes);
    for (LogPipeBase pipe : pipes) {
      pipe.open();
    }
  }

  public void stopListeningAllLogs() {
    for (String deploymentName : new ArrayList<String>(myDeploymentName2ActiveLogPipes.keySet())) {
      stopListeningLog(deploymentName);
    }
  }

  public void stopListeningLog(String deploymentName) {
    List<LogPipeBase> pipes = myDeploymentName2ActiveLogPipes.remove(deploymentName);
    if (pipes != null) {
      for (LogPipeBase pipe : pipes) {
        pipe.close();
      }
    }
  }
}
