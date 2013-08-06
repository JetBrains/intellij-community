package com.intellij.remoteServer.impl.runtime;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeployToServerRunner extends DefaultProgramRunner {
  @NotNull
  @Override
  public String getRunnerId() {
    return "DeployToServer";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof DeployToServerRunConfiguration;
  }
}
