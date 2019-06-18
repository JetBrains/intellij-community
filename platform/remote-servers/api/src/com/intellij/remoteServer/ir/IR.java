package com.intellij.remoteServer.ir;

import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;

import java.util.List;
import java.util.Map;

public class IR {

  interface PathId {
  }

  interface PortId {
    int getRemotePort();
  }

  interface RemoteRunner {
    Platform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request) throws Exception;
  }

  interface RemoteEnvironment {
    Platform getPlatform();

    Map<String, String> getEnvVars();

    String findRemotePath(PathId pathId);

    ProcessHandler createProcessHandler(NewCommandLine commandLine);
  }

  interface RemoteEnvironmentRequest {
    PathId requestTransfer(String localPath);

    PathId requestRemoteFile(String remotePath);

    PortId requestPortMapping(int remotePort);
  }

  interface NewCommandLine {

    /**
     * {@link GeneralCommandLine#getPreparedCommandLine()}
     */
    List<String> prepareCommandLine(RemoteEnvironment target);

    void setExePath(PathId path);

    void setWorkingDirectory(PathId path);

    void addPathParameter(String name, PathId value);

    void addEnvVar(String name, PathId value);
  }
}
