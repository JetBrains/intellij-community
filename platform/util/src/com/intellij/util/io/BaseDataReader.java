// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseDataReader {
  private static final Logger LOG = Logger.getInstance(BaseDataReader.class);

  protected final SleepingPolicy mySleepingPolicy;
  protected final Object mySleepMonitor = new Object();
  protected volatile boolean isStopped;

  private Future<?> myFinishedFuture;

  /**
   * @param sleepingPolicy default is {@link SleepingPolicy#NON_BLOCKING} for the reasons described on {@link SleepingPolicy} which may be changed
   *                       in future versions.
   */
  @ReviseWhenPortedToJDK("Loom")
  public BaseDataReader(SleepingPolicy sleepingPolicy) {
    mySleepingPolicy = sleepingPolicy != null ? sleepingPolicy : SleepingPolicy.NON_BLOCKING;
  }

  protected void start(@NotNull @NonNls String presentableName) {
    if (StringUtilRt.isEmptyOrSpaces(presentableName)) {
      LOG.warn(new Throwable("Must provide not-empty presentable name"));
    }
    if (myFinishedFuture == null) {
      myFinishedFuture = executeOnPooledThread(() -> {
        if (StringUtilRt.isEmptyOrSpaces(presentableName)) {
          doRun();
        }
        else {
          ConcurrencyUtil.runUnderThreadName("BaseDataReader: " + presentableName, this::doRun);
        }
      });
    }
  }

  @ApiStatus.Internal
  protected void startWithoutChangingThreadName() {
    if (myFinishedFuture == null) {
      myFinishedFuture = executeOnPooledThread(() -> doRun());
    }
  }

  /**
   * Please don't override this method as the BaseOSProcessProcessHandler assumes that it can be two reading modes: blocking and non-blocking.
   * Implement {@link #readAvailableBlocking} and {@link #readAvailableNonBlocking} instead.
   *
   * If the process handler assumes that reader handles the blocking mode, while it doesn't, it will result into premature stream close.
   *
   * @return true in case any data was read
   * @see SleepingPolicy
   * @throws IOException if an exception during IO happened
   */
  protected boolean readAvailable() throws IOException {
    return mySleepingPolicy == SleepingPolicy.BLOCKING ? readAvailableBlocking() : readAvailableNonBlocking();
  }

  /**
   * Non-blocking read returns the control back to the process handler when there is no data to read.
   * @see SleepingPolicy#NON_BLOCKING
   */
  protected boolean readAvailableNonBlocking() throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Reader in a blocking mode blocks on IO read operation until data is received. It exits the method only after the stream is closed.
   * @see SleepingPolicy#BLOCKING
   */
  protected boolean readAvailableBlocking() throws IOException {
    throw new UnsupportedOperationException();
  }

  protected abstract @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable);

  /**
   * <h2>Blocking</h2>
   * In Java you can only read data from child process's stdout/stderr using blocking {@link InputStream#read()}.
   * (Async approach like {@link java.nio.channels.SelectableChannel} is not supported for process's streams,
   * although some native api may be used).
   * Thread stays blocked by {@link InputStream#read()} until some data arrived or stream is closed (because of process death).
   * It may lead to issues like {@code IDEA-32376}: you can't unlock blocked thread (at least non-daemon) otherwise than by killing
   * process (and you may want to keep it running). {@link Thread#interrupt()} doesn't work here.
   * This approach is good for short-living processes.
   * If you know for sure that process will end soon (i.e. helper process) you can enable this behaviour using {@link #BLOCKING} policy.
   * It is implemented in {@link #readAvailableBlocking()}
   *
   * <h2>Non-blocking</h2>
   * Before reading data, you can call {@link InputStream#available()} to see how much data can be read without of blocking.
   * This gives us ability to use simple loop
   * <ol>
   * <li>Check {@link InputStream#available()}</li>
   * <li>If not zero then {@link InputStream#read()}} which is guaranteed not to block </li>
   * <li>If {@code processTerminated} flag set then exit loop</li>
   * <li>Sleep for a while</li>
   * <li>Repeat</li>
   * </ol>
   * This "busy-wait" anti-pattern is the only way to exit thread leaving process alive. It is required if you want to "disconnect" from
   * user process and used by {@link #NON_BLOCKING} (aka non-blocking) policy. Drawback is that process may finish (when {@link Process#waitFor()} returns)
   * leaving some data unread.
   * It is implemented in {@link #readAvailableNonBlocking()}}
   *
   * <h2>Conclusion</h2>
   * For helper (simple script that is guaranteed to finish soon) and should never be left after IDE is closed use {@link #BLOCKING}.
   * For user process that may run forever, even after idea is closed, and user should have ability to disconnect from it
   * use {@link #NON_BLOCKING}.
   * If you see some data lost in stdout/stderr try switching to {@link #BLOCKING}.
   */
  @ReviseWhenPortedToJDK("Loom")
  public interface SleepingPolicy {
    int sleepTimeWhenWasActive = 1;
    int sleepTimeWhenIdle = 5;

    SleepingPolicy NON_BLOCKING = wasActive -> wasActive ? sleepTimeWhenWasActive : sleepTimeWhenIdle;

    SleepingPolicy BLOCKING = __ -> {
      // in the blocking mode we need to sleep only when we have reached the end of the stream, so it can be a long sleeping
      return 50;
    };


    int getTimeToSleep(boolean wasActive);

    /**
     * @deprecated use {@link #NON_BLOCKING} instead
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    SleepingPolicy SIMPLE = NON_BLOCKING;
  }

  protected void doRun() {
    try {
      boolean stopSignalled = false;
      while (true) {
        final boolean read = readAvailable();

        if (stopSignalled || mySleepingPolicy == SleepingPolicy.BLOCKING) {
          break;
        }

        stopSignalled = isStopped;

        if (!stopSignalled) {
          // if process stopped, there is no sense to sleep, just check if there is unread output in the stream
          beforeSleeping(read);
          synchronized (mySleepMonitor) {
            mySleepMonitor.wait(mySleepingPolicy.getTimeToSleep(read));
          }
        }
      }
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (Exception e) {
      if (!(e instanceof ControlFlowException)) {
        LOG.error(e);
      }
    }
    finally {
      flush();
      try {
        close();
      }
      catch (IOException e) {
        LOG.error("Can't close stream", e);
      }
    }
  }

  protected void flush() {}

  protected void beforeSleeping(boolean hasJustReadSomething) {}

  protected abstract void close() throws IOException;

  public void stop() {
    isStopped = true;
    synchronized (mySleepMonitor) {
      mySleepMonitor.notifyAll();
    }
  }

  public void waitFor() throws InterruptedException {
    try {
      myFinishedFuture.get();
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  public void waitFor(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    try {
      myFinishedFuture.get(timeout, unit);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }
}
