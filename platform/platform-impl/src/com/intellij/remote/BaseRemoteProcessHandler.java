/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessWaitFor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

/**
 * @author traff
 */
public class BaseRemoteProcessHandler<T extends RemoteProcess> extends AbstractRemoteProcessHandler<T> implements TaskExecutor {
  private static final Logger LOG = Logger.getInstance(BaseRemoteProcessHandler.class);

  protected final String myCommandLine;
  protected final ProcessWaitFor myWaitFor;
  protected final Charset myCharset;
  protected T myProcess;

  public BaseRemoteProcessHandler(@NotNull T process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    myProcess = process;
    myCommandLine = commandLine;
    myWaitFor = new ProcessWaitFor(process, this, CommandLineUtil.extractPresentableName(commandLine));
    myCharset = charset;
    if (StringUtil.isEmpty(commandLine)) {
      LOG.warn(new IllegalArgumentException("Must specify non-empty 'commandLine' parameter"));
    }
  }

  @Override
  public T getProcess() {
    return myProcess;
  }

  @Override
  protected void destroyProcessImpl() {
    if (!myProcess.killProcessTree()) {
      baseDestroyProcessImpl();
    }
  }

  @Override
  public void startNotify() {
    notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull final ProcessEvent event) {
        try {
          final RemoteOutputReader stdoutReader = new RemoteOutputReader(myProcess.getInputStream(), getCharset(), myProcess, myCommandLine) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            }

            @NotNull
            @Override
            protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.executeOnPooledThread(runnable);
            }
          };

          final RemoteOutputReader stderrReader = new RemoteOutputReader(myProcess.getErrorStream(), getCharset(), myProcess, myCommandLine) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDERR);
            }

            @NotNull
            @Override
            protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.executeOnPooledThread(runnable);
            }
          };

          myWaitFor.setTerminationCallback(exitCode -> {
            try {
              try {
                stderrReader.waitFor();
                stdoutReader.waitFor();
              }
              catch (InterruptedException ignore) { }
            }
            finally {
              onOSProcessTerminated(exitCode);
            }
          });
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  protected void onOSProcessTerminated(final int exitCode) {
    notifyProcessTerminated(exitCode);
  }

  protected void baseDestroyProcessImpl() {
    try {
      closeStreams();
    }
    finally {
      doDestroyProcess();
    }
  }

  protected void doDestroyProcess() {
    getProcess().destroy();
  }

  @Override
  protected void detachProcessImpl() {
    final Runnable runnable = () -> {
      closeStreams();

      myWaitFor.detach();
      notifyProcessDetached();
    };

    executeOnPooledThread(runnable);
  }

  protected void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  @NotNull
  private static Future<?> executeOnPooledThread(@NotNull Runnable task) {
    return AppExecutorUtil.getAppExecutorService().submit(task);
  }

  @NotNull
  @Override
  public Future<?> executeTask(@NotNull Runnable task) {
    return executeOnPooledThread(task);
  }

  private abstract static class RemoteOutputReader extends BaseOutputReader {
    @NotNull private final RemoteProcess myRemoteProcess;
    private boolean myClosed;

    RemoteOutputReader(@NotNull InputStream inputStream, Charset charset, @NotNull RemoteProcess remoteProcess, @NotNull String commandLine) {
      super(inputStream, charset);

      myRemoteProcess = remoteProcess;

      start(CommandLineUtil.extractPresentableName(commandLine));
    }

    @Override
    protected void doRun() {

      try {
        setClosed(false);
        while (true) {
          final boolean read = readAvailable();

          if (myRemoteProcess.isDisconnected()) {
            myReader.close();
            break;
          }

          if (isStopped) {
            break;
          }

          Thread.sleep(mySleepingPolicy.getTimeToSleep(read)); // give other threads a chance
        }
      }
      catch (InterruptedException ignore) {
      }
      catch (Exception e) {
        LOG.warn(e);
      }
      finally {
        setClosed(true);
      }
    }

    protected synchronized void setClosed(boolean closed) {
      myClosed = closed;
    }

    @Override
    public void waitFor() throws InterruptedException {
      while (!isClosed()) {
        Thread.sleep(100);
      }
    }

    private synchronized boolean isClosed() {
      return myClosed;
    }
  }

  @Nullable
  public String getCommandLine() {
    return myCommandLine;
  }
}