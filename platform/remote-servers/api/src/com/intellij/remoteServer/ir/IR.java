package com.intellij.remoteServer.ir;

import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;

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
    
    RemoteValue createPathValue(@NotNull String path);

    RemoteValue createPortValue(int port);

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request) throws Exception;
  }

  public interface RemoteEnvironment {
    Platform getPlatform();

    Map<String, String> getEnvVars();

    // todo: string or remoteValue or pathId?
    String findRemotePath(String path);

    ProcessHandler createProcessHandler(NewCommandLine commandLine);
  }

  public interface RemoteEnvironmentRequest {
    PathId requestTransfer(String localPath);

    PathId requestRemoteFile(String remotePath);

    PortId requestPortMapping(int remotePort);
  }

  public interface RemoteValue {
    @NotNull
    String toString(@NotNull RemoteEnvironment environment);
  }

  public class StringRemoteValue implements RemoteValue {
    private final String myString;

    public StringRemoteValue(@NotNull String string) {
      myString = string;
    }

    @Override
    @NotNull
    public String toString(@NotNull RemoteEnvironment environment) {
      return myString;
    }
  }

  public interface NewCommandLine {

    /**
     * {@link GeneralCommandLine#getPreparedCommandLine()}
     */
    List<String> prepareCommandLine(@NotNull RemoteEnvironment target);

    void setExePath(RemoteValue exePath);

    void setWorkingDirectory(RemoteValue workingDirectory);

    void addParameter(RemoteValue parameter);

    void addEnvironmentVariable(String name, RemoteValue value);
  }
}
