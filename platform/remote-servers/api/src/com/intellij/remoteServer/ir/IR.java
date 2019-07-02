package com.intellij.remoteServer.ir;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.progress.ProgressIndicator;
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
    //static Process runSimpleProcess(String commandLine, ProgressIndicator indicator) {
    //  return null;
    //}

    @NotNull
    RemotePlatform getRemotePlatform();

    RemoteEnvironmentRequest createRequest();

    RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request, ProgressIndicator indicator);
  }

  public interface RemoteEnvironment {
    RemotePlatform getRemotePlatform();

    Process createProcess(NewCommandLine commandLine, ProgressIndicator indicator) throws ExecutionException;
  }

  public interface RemoteEnvironmentRequest {
    RemotePlatform getRemotePlatform();

    //TODO: change to Path
    RemoteValue<String> createUpload(@NotNull String localPath);

    RemoteValue<Integer> bindRemotePort(int remotePort);

    RemoteLocation createRemoteLocation(@Nullable String remotePath/*, @Nullable Object __options*/);

    RemoteLocation importLocation(RemoteLocation otherLoc, RemoteEnvironment originalEnv);
  }

  public interface RemoteValue<T> {

    RemoteValue EMPTY_VALUE = new RemoteValue() {
      @Override
      public Object getLocalValue() {
        return null;
      }

      @Override
      public Object getRemoteValue() {
        return null;
      }
    };

    static <V> RemoteValue<V> EMPTY_VALUE() {
      //noinspection unchecked
      return EMPTY_VALUE;
    }

    //todo[remoteServers]: rename? it's easy to accidentally use toString() instead of toString(env)
    //@Nullable
    //String toString(@NotNull RemoteEnvironment environment);

    T getLocalValue();

    T getRemoteValue();
  }

  public interface RemoteLocation {
    void dispose();

    String getRemotePath();

    //Path download(ProgressIndicator indicator);
  }

  public static class FixedValue<T> implements RemoteValue<T> {
    private final T myValue;

    public FixedValue(@NotNull T value) {
      myValue = value;
    }

    @Override
    public T getRemoteValue() {
      return myValue;
    }

    @Override
    public T getLocalValue() {
      return myValue;
    }
  }

  public static class StringFixedValue extends FixedValue<String> {

    public StringFixedValue(@NotNull String string) {
      super(string);
    }
  }

  public static class NewCommandLine {
    private RemoteValue<String> myExePath = RemoteValue.EMPTY_VALUE();
    private RemoteValue<String> myWorkingDirectory = RemoteValue.EMPTY_VALUE();
    private final List<RemoteValue<String>> myParameters = new ArrayList<>();
    private final Map<String, RemoteValue<String>> myEnvironment = new HashMap<>();

    /**
     * {@link GeneralCommandLine#getPreparedCommandLine()}
     */
    public List<String> prepareCommandLine(@NotNull RemoteEnvironment target) {
      String command = myExePath.getRemoteValue();
      if (command == null) {
        // todo: handle this properly
        throw new RuntimeException("Cannot find command");
      }
      return CommandLineUtil.toCommandLine(command, getParameters(target), target.getRemotePlatform().getPlatform());
    }

    public void setExePath(@NotNull RemoteValue<String> exePath) {
      myExePath = exePath;
    }

    public void setExePath(@NotNull String exePath) {
      myExePath = new StringFixedValue(exePath);
    }

    public void setWorkingDirectory(@NotNull RemoteValue<String> workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    public void addParameter(@NotNull RemoteValue<String> parameter) {
      myParameters.add(parameter);
    }

    public void addParameter(@NotNull String parameter) {
      myParameters.add(new StringFixedValue(parameter));
    }

    public void addEnvironmentVariable(String name, RemoteValue value) {
      myEnvironment.put(name, value);
    }

    public void addEnvironmentVariable(String name, String value) {
      myEnvironment.put(name, new StringFixedValue(value));
    }

    //todo: all `target`s are not used
    public String getExePath(@NotNull RemoteEnvironment target) {
      return myExePath.getRemoteValue();
    }

    @Nullable
    public String getWorkingDirectory(@NotNull RemoteEnvironment target) {
      return myWorkingDirectory.getRemoteValue();
    }

    @NotNull
    public List<String> getParameters(@NotNull RemoteEnvironment target) {
      return ContainerUtil.mapNotNull(myParameters, RemoteValue::getRemoteValue);
    }

    @NotNull
    public Map<String, String> getEnvironmentVariables(@NotNull RemoteEnvironment target) {
      return ContainerUtil.map2MapNotNull(myEnvironment.entrySet(), e -> {
        String value = e.getValue().getRemoteValue();
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
        public RemotePlatform getRemotePlatform() {
          return LocalRunner.this.getRemotePlatform();
        }

        @Override
        public RemoteValue<String> createUpload(@NotNull String localPath) {
          return new StringFixedValue(localPath);
        }

        @Override
        public RemoteValue<Integer> bindRemotePort(int remotePort) {
          return new FixedValue<>(remotePort);
        }

        @Override
        public RemoteLocation createRemoteLocation(@Nullable String remotePath) {
          return null;
        }

        @Override
        public RemoteLocation importLocation(RemoteLocation otherLoc, RemoteEnvironment originalEnv) {
          return null;
        }
      };
    }

    @Override
    public RemoteEnvironment prepareRemoteEnvironment(RemoteEnvironmentRequest request, ProgressIndicator indicator) {
      return new LocalRemoteEnvironment();
    }
  }

  public static class LocalRemoteEnvironment implements RemoteEnvironment {
    @Override
    public RemotePlatform getRemotePlatform() {
      return RemotePlatform.CURRENT;
    }

    @Override
    public Process createProcess(NewCommandLine commandLine, ProgressIndicator indicator) throws ExecutionException {
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
