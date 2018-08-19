// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseInputStreamReader;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.BaseOutputReader.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BaseOSProcessHandler extends BaseProcessHandler<Process> {
  private static final Logger LOG = Logger.getInstance(BaseOSProcessHandler.class);

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public BaseOSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  /**
   * Override this method in order to execute the task with a custom pool
   *
   * @param task a task to run
   * @deprecated override {@link #executeTask(Runnable)} instead of this method
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  protected Future<?> executeOnPooledThread(@NotNull final Runnable task) {
    return ProcessIOExecutorService.INSTANCE.submit(task);
  }

  @Override
  @NotNull
  public Future<?> executeTask(@NotNull Runnable task) {
    return executeOnPooledThread(task);
  }

  /**
   * Override this method to fine-tune {@link BaseOutputReader} behavior.
   */
  @NotNull
  @SuppressWarnings("deprecation")
  protected Options readerOptions() {
    if (Registry.is("output.reader.blocking.mode", false)) {
      return Options.BLOCKING;
    }
    else {
      return Options.NON_BLOCKING;
    }
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
      public void startNotified(@NotNull final ProcessEvent event) {
        try {
          Options options = readerOptions();
          @SuppressWarnings("deprecation") final BaseDataReader stdOutReader = createOutputDataReader(options.policy());
          @SuppressWarnings("deprecation") final BaseDataReader stdErrReader = processHasSeparateErrorStream() ? createErrorDataReader(options.policy()) : null;

          myWaitFor.setTerminationCallback(new Consumer<Integer>() {
            @Override
            public void consume(Integer exitCode) {
              try {
                // tell readers that no more attempts to read process' output should be made
                if (stdErrReader != null) stdErrReader.stop();
                stdOutReader.stop();

                try {
                  if (stdErrReader != null) stdErrReader.waitFor();
                  stdOutReader.waitFor();
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

  /** @deprecated override {@link #createOutputDataReader()} (to be removed in IDEA 2018) */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected BaseDataReader createErrorDataReader(@SuppressWarnings("UnusedParameters") BaseDataReader.SleepingPolicy policy) {
    return createErrorDataReader();
  }

  /** @deprecated override {@link #createOutputDataReader()} (to be removed in IDEA 2018) */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected BaseDataReader createOutputDataReader(@SuppressWarnings("UnusedParameters") BaseDataReader.SleepingPolicy policy) {
    return createOutputDataReader();
  }

  @NotNull
  protected BaseDataReader createErrorDataReader() {
    return new SimpleOutputReader(createProcessErrReader(), ProcessOutputTypes.STDERR, readerOptions(), "error stream of " + myPresentableName);
  }

  @NotNull
  protected BaseDataReader createOutputDataReader() {
    return new SimpleOutputReader(createProcessOutReader(), ProcessOutputTypes.STDOUT, readerOptions(), "output stream of " + myPresentableName);
  }

  @NotNull
  protected Reader createProcessOutReader() {
    return createInputStreamReader(myProcess.getInputStream());
  }

  @NotNull
  protected Reader createProcessErrReader() {
    return createInputStreamReader(myProcess.getErrorStream());
  }

  @NotNull
  private Reader createInputStreamReader(@NotNull InputStream streamToRead) {
    Charset charset = getCharset();
    if (charset == null) charset = Charset.defaultCharset();
    return new BaseInputStreamReader(streamToRead, charset);
  }

  /** @deprecated use {@link BaseOSProcessHandler#executeTask(Runnable)} instead (to be removed in IDEA 2018) */
  @Deprecated
  public static class ExecutorServiceHolder {
    public static Future<?> submit(@NotNull Runnable task) {
      LOG.warn("Deprecated method. Please use com.intellij.execution.process.BaseOSProcessHandler.executeTask() instead", new Throwable());
      return AppExecutorUtil.getAppExecutorService().submit(task);
    }
  }

  protected class SimpleOutputReader extends BaseOutputReader {
    private final Key myProcessOutputType;

    public SimpleOutputReader(Reader reader, Key outputType, Options options, @NotNull String presentableName) {
      super(reader, options);
      myProcessOutputType = outputType;
      start(presentableName);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return BaseOSProcessHandler.this.executeTask(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      notifyTextAvailable(text, myProcessOutputType);
    }
  }

  @Override
  public String toString() {
    return myCommandLine;
  }

  @Override
  public boolean waitFor() {
    boolean result = super.waitFor();
    try {
      myWaitFor.waitFor();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  @Override
  public boolean waitFor(long timeoutInMilliseconds) {
    long start = System.currentTimeMillis();
    boolean result = super.waitFor(timeoutInMilliseconds);
    long elapsed = System.currentTimeMillis() - start;
    try {
      result &= myWaitFor.waitFor(Math.max(0, timeoutInMilliseconds-elapsed), TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return result;
  }
}