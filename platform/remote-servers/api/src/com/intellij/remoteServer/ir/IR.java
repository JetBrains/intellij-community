package com.intellij.remoteServer.ir;

import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;

import java.util.List;
import java.util.Map;

public class IR {

  public interface PathId {
  }

  public interface PortId {
    int getRemotePort();
  }

  public interface RemoteRunner {
    Platform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request) throws Exception;
  }

  public interface RemoteEnvironment {
    Platform getPlatform();

    Map<String, String> getEnvVars();

    String findRemotePath(PathId pathId);

    ProcessHandler createProcessHandler(NewCommandLine commandLine);
  }

  public interface RemoteEnvironmentRequest {
    PathId requestTransfer(String localPath);

    PathId requestRemoteFile(String remotePath);

    PortId requestPortMapping(int remotePort);
  }

  public interface NewCommandLine {

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
