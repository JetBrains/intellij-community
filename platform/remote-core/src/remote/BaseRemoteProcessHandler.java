// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

public class BaseRemoteProcessHandler<T extends RemoteProcess> extends BaseProcessHandler<T> {
  private static final Logger LOG = Logger.getInstance(BaseRemoteProcessHandler.class);

  private final ModalityState myModality;

  public BaseRemoteProcessHandler(@NotNull T process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
    myModality = OSProcessHandler.getDefaultModality();
  }

  /**
   * Override this method to fine-tune {@link BaseOutputReader} behavior.
   */
  protected @NotNull BaseOutputReader.Options readerOptions() {
    return new BaseOutputReader.Options();
  }

  @Override
  protected void destroyProcessImpl() {
    if (!myProcess.killProcessTree()) {
      super.destroyProcessImpl();
    }
  }

  @Override
  protected void onOSProcessTerminated(int exitCode) {
    if (myModality != ModalityState.nonModal()) {
      ProgressManager.getInstance().runProcess(() -> super.onOSProcessTerminated(exitCode), new EmptyProgressIndicator(myModality));
    }
    else {
      super.onOSProcessTerminated(exitCode);
    }
  }

  @Override
  public void startNotify() {
    notifyTextAvailable(getCommandLineForLog() + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(final @NotNull ProcessEvent event) {
        try {
          var stdoutReader = new RemoteOutputReader(myProcess.getInputStream(), getCharset(), myProcess, getCommandLineForLog(), readerOptions()) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            }

            @Override
            protected @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.this.executeTask(runnable);
            }
          };

          var stderrReader = new RemoteOutputReader(myProcess.getErrorStream(), getCharset(), myProcess, getCommandLineForLog(), readerOptions()) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDERR);
            }

            @Override
            protected @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.this.executeTask(runnable);
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

  @Override
  public @NotNull Future<?> executeTask(@NotNull Runnable task) {
    return AppExecutorUtil.getAppExecutorService().submit(task);
  }

  private abstract static class RemoteOutputReader extends BaseOutputReader {
    private final RemoteProcess myRemoteProcess;
    private boolean myClosed;

    RemoteOutputReader(InputStream inputStream, @Nullable Charset charset, RemoteProcess remoteProcess, String commandLine, Options options) {
      super(inputStream, charset, options);

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
      catch (InterruptedException ignore) { }
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
}
