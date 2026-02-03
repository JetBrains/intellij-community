// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseInputStreamReader;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.BaseOutputReader.Options;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BaseOSProcessHandler extends BaseProcessHandler<Process> {
  private static final Logger LOG = Logger.getInstance(BaseOSProcessHandler.class);
  private final AtomicLong mySleepStart = new AtomicLong(System.currentTimeMillis());
  private final Throwable myProcessStart;

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public BaseOSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
    myProcessStart = new Throwable("Process creation:");
  }

  @Override
  public @NotNull Future<?> executeTask(@NotNull Runnable task) {
    return ProcessIOExecutorService.INSTANCE.submit(task);
  }

  /**
   * Override this method to fine-tune {@link BaseOutputReader} behavior.
   */
  protected @NotNull Options readerOptions() {
    if (Boolean.getBoolean("output.reader.blocking.mode")) {
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
    if (getCommandLineForLog() != null) {
      notifyTextAvailable(getCommandLineForLog() + '\n', ProcessOutputTypes.SYSTEM);
    }

    addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(final @NotNull ProcessEvent event) {
        try {
          BaseDataReader stdOutReader = createOutputDataReader();
          BaseDataReader stdErrReader = processHasSeparateErrorStream() ? createErrorDataReader() : null;

          myWaitFor.setTerminationCallback(exitCode -> {
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
          });
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  protected @NotNull BaseDataReader createErrorDataReader() {
    return new SimpleOutputReader(createProcessErrReader(), ProcessOutputTypes.STDERR, readerOptions(), "error stream of " + myPresentableName);
  }

  protected @NotNull BaseDataReader createOutputDataReader() {
    return new SimpleOutputReader(createProcessOutReader(), ProcessOutputTypes.STDOUT, readerOptions(), "output stream of " + myPresentableName);
  }

  protected @NotNull Reader createProcessOutReader() {
    return createInputStreamReader(myProcess.getInputStream());
  }

  protected @NotNull Reader createProcessErrReader() {
    return createInputStreamReader(myProcess.getErrorStream());
  }

  private @NotNull Reader createInputStreamReader(@NotNull InputStream streamToRead) {
    Charset charset = getCharset();
    if (charset == null) charset = Charset.defaultCharset();
    return new BaseInputStreamReader(streamToRead, charset);
  }

  protected final class SimpleOutputReader extends BaseOutputReader {
    private final Key<?> myProcessOutputType;

    public SimpleOutputReader(Reader reader, Key<?> outputType, Options options, @NotNull @NonNls String presentableName) {
      super(reader, options);
      myProcessOutputType = outputType;
      start(presentableName);
    }

    @Override
    protected @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return BaseOSProcessHandler.this.executeTask(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      notifyTextAvailable(text, myProcessOutputType);
    }

    @Override
    protected void beforeSleeping(boolean hasJustReadSomething) {
      long sleepStart = mySleepStart.get();
      if (sleepStart < 0) return;

      long now = System.currentTimeMillis();
      if (hasJustReadSomething) {
        mySleepStart.set(now);
      }
      else if (TimeUnit.MILLISECONDS.toMinutes(now - sleepStart) >= 2 &&
               mySleepStart.compareAndSet(sleepStart, -1)) { // report only once
        LOG.warn("Process hasn't generated any output for a long time.\n" +
                 "If it's a long-running mostly idle daemon process, consider overriding OSProcessHandler#readerOptions with" +
                 " 'BaseOutputReader.Options.forMostlySilentProcess()' to reduce CPU usage.\n" +
                 "Command line: " + StringUtil.trimLog(StringUtil.notNullize(getCommandLineForLog()), 1000),
                 myProcessStart);
      }
    }
  }

  @Override
  public String toString() {
    return getCommandLineForLog();
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