package com.intellij.remoteServer.ir;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
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
    RemoteValue EMPTY_VALUE = e -> null;

    @Nullable
    String toString(@NotNull RemoteEnvironment environment);
  }

  public static class StringRemoteValue implements RemoteValue {
    private final String myString;

    public StringRemoteValue(@NotNull String string) {
      myString = string;
    }

    @Nullable
    @Override
    public String toString(@NotNull RemoteEnvironment environment) {
      return myString;
    }
  }

  public static class NewCommandLine {
    private RemoteValue myExePath = RemoteValue.EMPTY_VALUE;
    private RemoteValue myWorkingDirectory = RemoteValue.EMPTY_VALUE;
    private final List<RemoteValue> myParameters = new ArrayList<>();
    private final Map<String, RemoteValue> myEnvironment = new HashMap<>();

    /**
     * {@link GeneralCommandLine#getPreparedCommandLine()}
     */
    public List<String> prepareCommandLine(@NotNull RemoteEnvironment target) {
      // todo something with working directory and environment?
      List<String> parameters = ContainerUtil.mapNotNull(myParameters, v -> v.toString(target));
      return CommandLineUtil.toCommandLine(myExePath.toString(), parameters, target.getPlatform());
    }

    public void setExePath(@NotNull RemoteValue exePath) {
      myExePath = exePath;
    }

    public void setWorkingDirectory(@NotNull RemoteValue workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    public void addParameter(@NotNull RemoteValue parameter) {
      myParameters.add(parameter);
    }

    public void addEnvironmentVariable(String name, RemoteValue value) {
      myEnvironment.put(name, value);
    }
  }

  public static class LocalRunner implements RemoteRunner {
    @Override
    public Platform getRemotePlatform() {
      return Platform.current();
    }
  
    @Override
    public RemoteValue createPathValue(@NotNull String path) {
      return new StringRemoteValue(path);
    }
  
    @Override
    public RemoteValue createPortValue(int port) {
      return e -> String.valueOf(port);
    }
  
    @Override
    public RemoteEnvironmentRequest createRequest() {
      return null;
    }
  
    @Override
    public RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request) throws Exception {
      return null;
    }
  }
}
