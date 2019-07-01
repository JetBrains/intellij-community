package com.intellij.remoteServer.ir;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
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
    @NotNull
    RemotePlatform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request);
  }

  public interface RemoteEnvironment {
    RemotePlatform getRemotePlatform();

    // todo: string or remoteValue or pathId?
    //String findRemotePath(String path);

    Process createProcess(NewCommandLine commandLine) throws ExecutionException;
  }

  public interface RemoteEnvironmentRequest {
    RemoteValue createPathValue(@NotNull String path);

    RemoteValue createPortValue(int port);
  }

  public interface RemoteValue {
    RemoteValue EMPTY_VALUE = e -> null;

    //todo[remoteServers]: rename? it's easy to accidentally use toString() instead of toString(env)
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
      String command = myExePath.toString(target);
      if (command == null) {
        // todo: handle this properly
        throw new RuntimeException("Cannot find command");
      }
      return CommandLineUtil.toCommandLine(command, getParameters(target), target.getRemotePlatform().getPlatform());
    }

    public void setExePath(@NotNull RemoteValue exePath) {
      myExePath = exePath;
    }

    public void setExePath(@NotNull String exePath) {
      myExePath = new StringRemoteValue(exePath);
    }

    public void setWorkingDirectory(@NotNull RemoteValue workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    public void addParameter(@NotNull RemoteValue parameter) {
      myParameters.add(parameter);
    }

    public void addParameter(@NotNull String parameter) {
      myParameters.add(new StringRemoteValue(parameter));
    }

    public void addEnvironmentVariable(String name, RemoteValue value) {
      myEnvironment.put(name, value);
    }

    public void addEnvironmentVariable(String name, String value) {
      myEnvironment.put(name, new StringRemoteValue(value));
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
      return ContainerUtil.map2MapNotNull(myEnvironment.entrySet(), e -> {
        String value = e.getValue().toString(target);
        return value != null ? Pair.create(e.getKey(), value) : null;
      });
    }
  }

  public static class LocalRunner implements RemoteRunner {
    @NotNull
    @Override
    public RemotePlatform getRemotePlatform() {
      return RemotePlatform.CURRENT;
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
    public RemotePlatform getRemotePlatform() {
      return RemotePlatform.CURRENT;
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
