/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.TaskExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.*;

import static com.intellij.util.io.BaseDataReader.AdaptiveSleepingPolicy;

public class BaseOSProcessHandler extends ProcessHandler implements TaskExecutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandlerBase");

  @NotNull protected final Process myProcess;
  @Nullable protected final String myCommandLine;
  protected final ProcessWaitFor myWaitFor;
  @Nullable protected final Charset myCharset;

  public BaseOSProcessHandler(@NotNull final Process process, @Nullable final String commandLine, @Nullable Charset charset) {
    myProcess = process;
    myCommandLine = commandLine;
    myCharset = charset;
    myWaitFor = new ProcessWaitFor(process, this);
  }

  /**
   * Override this method in order to execute the task with a custom pool
   *
   * @param task a task to run
   */
  protected Future<?> executeOnPooledThread(Runnable task) {
    return ExecutorServiceHolder.ourThreadExecutorsService.submit(task);
  }

  @Override
  public Future<?> executeTask(Runnable task) {
    return executeOnPooledThread(task);
  }

  @NotNull
  public Process getProcess() {
    return myProcess;
  }

  protected boolean useAdaptiveSleepingPolicyWhenReadingOutput() {
    return false;
  }

  protected boolean processHasSeparateErrorStream() {
    return true;
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
          BaseDataReader.SleepingPolicy sleepingPolicy =
            useAdaptiveSleepingPolicyWhenReadingOutput() ? new AdaptiveSleepingPolicy() : BaseDataReader.SleepingPolicy.SIMPLE;
          final BaseDataReader stdoutReader = createOutputDataReader(sleepingPolicy);
          final BaseDataReader stderrReader = processHasSeparateErrorStream() ? createErrorDataReader(sleepingPolicy) : null;

          myWaitFor.setTerminationCallback(new Consumer<Integer>() {
            @Override
            public void consume(Integer exitCode) {
              try {
                // tell readers that no more attempts to read process' output should be made
                if (stderrReader != null) stderrReader.stop();
                stdoutReader.stop();

                try {
                  if (stderrReader != null) stderrReader.waitFor();
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

  @NotNull
  protected BaseDataReader createErrorDataReader(BaseDataReader.SleepingPolicy sleepingPolicy) {
    return new SimpleOutputReader(createProcessErrReader(), ProcessOutputTypes.STDERR,
                             sleepingPolicy);
  }

  @NotNull
  protected BaseDataReader createOutputDataReader(BaseDataReader.SleepingPolicy sleepingPolicy) {
    return new SimpleOutputReader(createProcessOutReader(), ProcessOutputTypes.STDOUT, sleepingPolicy);
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
      LOG.warn(e);
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
  public String getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  public static class ExecutorServiceHolder {
    private static final ExecutorService ourThreadExecutorsService = createServiceImpl();

    private static ThreadPoolExecutor createServiceImpl() {
      ThreadFactory factory = ConcurrencyUtil.newNamedThreadFactory("OSProcessHandler pooled thread");
      return new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), factory);
    }

    public static Future<?> submit(Runnable task) {
      return ourThreadExecutorsService.submit(task);
    }
  }

  private class SimpleOutputReader extends BaseOutputReader {
    private final Key myProcessOutputType;

    private SimpleOutputReader(@NotNull Reader reader, @NotNull Key processOutputType, SleepingPolicy sleepingPolicy) {
      super(reader, sleepingPolicy);
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
