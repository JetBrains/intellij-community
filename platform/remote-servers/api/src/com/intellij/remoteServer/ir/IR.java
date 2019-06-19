package com.intellij.remoteServer.ir;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IR {
  public interface RemoteRunner {
    Platform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request);
  }

  public interface RemoteEnvironment {
    Platform getPlatform();

    // todo: string or remoteValue or pathId?
    String findRemotePath(String path);

    Process createProcess(NewCommandLine commandLine) throws ExecutionException;
  }

  public interface RemoteEnvironmentRequest {
    RemoteValue createPathValue(@NotNull String path);

    RemoteValue createPortValue(int port);
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
      return CommandLineUtil.toCommandLine(myExePath.toString(), getParameters(target), target.getPlatform());
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

    public String getExePath(@NotNull RemoteEnvironment target) {
      return myExePath.toString(target);
    }

    @Nullable
    public String getWorkingDirectory(@NotNull RemoteEnvironment target) {
      return myWorkingDirectory.toString(target);
    }

    @NotNull
    public List<String> getParameters(@NotNull RemoteEnvironment target) {
      return ContainerUtil.mapNotNull(myParameters, v -> v.toString(target));
    }

    @NotNull
    public Map<String, String> getEnvironmentVariables(@NotNull RemoteEnvironment target) {
      return ContainerUtil.map2MapNotNull(myEnvironment.entrySet(), e -> Pair.create(e.getKey(), e.getValue().toString(target)));
    }
  }

  public static class LocalRunner implements RemoteRunner {
    @Override
    public Platform getRemotePlatform() {
      return Platform.current();
    }

    @Override
    public RemoteEnvironmentRequest createRequest() {
      return new RemoteEnvironmentRequest() {

        @Override
        public RemoteValue createPathValue(@NotNull String path) {
          return new StringRemoteValue(path);
        }

        @Override
        public RemoteValue createPortValue(int port) {
          return e -> String.valueOf(port);
        }
      };
    }

    @Override
    public RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request) {
      return new LocalRemoteEnvironment();
    }
  }

  public static class LocalRemoteEnvironment implements RemoteEnvironment {
    @Override
    public Platform getPlatform() {
      return Platform.current();
    }

    @Override
    public String findRemotePath(String path) {
      return path;
    }

    @Override
    public Process createProcess(NewCommandLine commandLine) throws ExecutionException {
      return createGeneralCommandLine(commandLine).createProcess();
    }

    @NotNull
    protected GeneralCommandLine createGeneralCommandLine(NewCommandLine commandLine) {
      GeneralCommandLine generalCommandLine = new GeneralCommandLine(commandLine.prepareCommandLine(this));
      String workingDirectory = commandLine.getWorkingDirectory(this);
      if (workingDirectory != null) {
        generalCommandLine.withWorkDirectory(workingDirectory);
      }
      generalCommandLine.withEnvironment(commandLine.getEnvironmentVariables(this));
      return generalCommandLine;
    }
  }
}
