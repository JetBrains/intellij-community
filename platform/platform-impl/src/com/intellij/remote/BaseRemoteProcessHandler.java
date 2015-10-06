package com.intellij.remote;

import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Consumer;
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
  @Nullable
  protected final String myCommandLine;
  protected final ProcessWaitFor myWaitFor;
  @Nullable
  protected final Charset myCharset;
  protected T myProcess;

  public BaseRemoteProcessHandler(@NotNull T process,
                                  @Nullable String commandLine,
                                  @Nullable Charset charset) {
    myProcess = process;
    myCommandLine = commandLine;
    myWaitFor = new ProcessWaitFor(process, this);
    myCharset = charset;
  }

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
    if (myCommandLine != null) {
      notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);
    }

    addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(final ProcessEvent event) {
        try {
          final RemoteOutputReader stdoutReader = new RemoteOutputReader(myProcess.getInputStream(), getCharset(), myProcess) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            }

            @Override
            protected Future<?> executeOnPooledThread(Runnable runnable) {
              return BaseRemoteProcessHandler.executeOnPooledThread(runnable);
            }
          };

          final RemoteOutputReader stderrReader = new RemoteOutputReader(myProcess.getErrorStream(), getCharset(), myProcess) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDERR);
            }

            @Override
            protected Future<?> executeOnPooledThread(Runnable runnable) {
              return BaseRemoteProcessHandler.executeOnPooledThread(runnable);
            }
          };

          myWaitFor.setTerminationCallback(new Consumer<Integer>() {
            @Override
            public void consume(Integer exitCode) {
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

  protected void detachProcessImpl() {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        closeStreams();

        myWaitFor.detach();
        notifyProcessDetached();
      }
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

  protected static Future<?> executeOnPooledThread(Runnable task) {
    final Application application = ApplicationManager.getApplication();

    if (application != null) {
      return application.executeOnPooledThread(task);
    }

    return BaseOSProcessHandler.ExecutorServiceHolder.submit(task);
  }

  @Override
  public Future<?> executeTask(Runnable task) {
    return executeOnPooledThread(task);
  }

  private abstract static class RemoteOutputReader extends BaseOutputReader {

    @NotNull private final RemoteProcess myRemoteProcess;
    private boolean myClosed;

    public RemoteOutputReader(@NotNull InputStream inputStream, Charset charset, @NotNull RemoteProcess remoteProcess) {
      super(inputStream, charset);

      myRemoteProcess = remoteProcess;

      start();
    }

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
      catch (IOException e) {
        LOG.warn(e);
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
