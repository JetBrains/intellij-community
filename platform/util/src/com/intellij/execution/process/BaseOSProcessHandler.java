/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.*;

public class BaseOSProcessHandler extends ProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandlerBase");

  @NotNull protected final Process myProcess;
  @Nullable protected final String myCommandLine;
  protected final ProcessWaitFor myWaitFor;
  @Nullable private final Charset myCharset;

  public BaseOSProcessHandler(@NotNull final Process process, @Nullable final String commandLine, @Nullable Charset charset) {
    myProcess = process;
    myCommandLine = commandLine;
    myCharset = charset;
    myWaitFor = new ProcessWaitFor(process);
  }

  /**
   * Override this method in order to execute the task with a custom pool
   *
   * @param task a task to run
   */
  protected Future<?> executeOnPooledThread(Runnable task) {
    return ExecutorServiceHolder.ourThreadExecutorsService.submit(task);
  }

  @NotNull
  public Process getProcess() {
    return myProcess;
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
          final BaseOutputReader stdoutReader = new SimpleOutputReader(createProcessOutReader(), ProcessOutputTypes.STDOUT);
          final BaseOutputReader stderrReader = new SimpleOutputReader(createProcessErrReader(), ProcessOutputTypes.STDERR);

          myWaitFor.setTerminationCallback(new Consumer<Integer>() {
            @Override
            public void consume(Integer exitCode) {
              try {
                // tell readers that no more attempts to read process' output should be made
                stderrReader.stop();
                stdoutReader.stop();

                try {
                  stderrReader.waitFor();
                  stdoutReader.waitFor();
                }
                catch (InterruptedException ignore) {
                }
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

  protected Reader createProcessOutReader() {
    return createInputStreamReader(myProcess.getInputStream());
  }

  protected Reader createProcessErrReader() {
    return createInputStreamReader(myProcess.getErrorStream());
  }

  private Reader createInputStreamReader(InputStream streamToRead) {
    final Charset charset = getCharset();
    if (charset == null) {
      // use default charset
      return new InputStreamReader(streamToRead);
    }
    return new InputStreamReader(streamToRead, charset);
  }

  @Override
  protected void destroyProcessImpl() {
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

  /** @deprecated internal use only (to remove in IDEA 13) */
  @Nullable
  public String getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  private static class ExecutorServiceHolder {
    private static final ExecutorService ourThreadExecutorsService = createServiceImpl();

    private static ThreadPoolExecutor createServiceImpl() {
      return new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
        @NotNull
        @Override
        @SuppressWarnings({"HardCodedStringLiteral"})
        public Thread newThread(@NotNull Runnable r) {
          return new Thread(r, "OSProcessHandler pooled thread");
        }
      });
    }
  }

  protected class ProcessWaitFor {
    private final Future<?> myWaitForThreadFuture;
    private final BlockingQueue<Consumer<Integer>> myTerminationCallback = new ArrayBlockingQueue<Consumer<Integer>>(1);

    public void detach() {
      myWaitForThreadFuture.cancel(true);
    }


    public ProcessWaitFor(final Process process) {
      myWaitForThreadFuture = executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          int exitCode = 0;
          try {
            while (true) {
              try {
                exitCode = process.waitFor();
                break;
              }
              catch (InterruptedException e) {
                LOG.debug(e);
              }
            }
          }
          finally {
            try {
              myTerminationCallback.take().consume(exitCode);
            }
            catch (InterruptedException e) {
              LOG.info(e);
            }
          }
        }
      });
    }

    public void setTerminationCallback(Consumer<Integer> r) {
      myTerminationCallback.offer(r);
    }
  }

  private class SimpleOutputReader extends BaseOutputReader {

    private final Key myProcessOutputType;

    private SimpleOutputReader(@NotNull Reader reader, @NotNull Key processOutputType) {
      super(reader);
      myProcessOutputType = processOutputType;
      start();
    }

    @Override
    protected Future<?> executeOnPooledThread(Runnable runnable) {
      return BaseOSProcessHandler.this.executeOnPooledThread(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      notifyTextAvailable(text, myProcessOutputType);
    }
  }
}
