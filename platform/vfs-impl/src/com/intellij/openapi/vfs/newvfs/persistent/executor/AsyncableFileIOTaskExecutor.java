// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.executor;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.ConcurrencyUtil;
import io.opentelemetry.api.metrics.Meter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.VFS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Responsible for file IO task execution in VFS.
 * <p>
 * Do some IO on the file either synchronously, or asynchronously, depending on params and state.
 * Current gates are:
 * <ol>
 *  <li>{@link FileIOTask#isAsyncExecutionAllowed()}: if it returns false => task always executed synchonously</li>
 *  <li>{@link BackpressureStrategy}: if installed backpressure strategy triggers a need for backpressure, Executor temporarily
 *  falls back to synchronous execution</li>
 * </ol>
 * <p>
 * <b>Recursion:</b> the current implementation does not support recursion -- {@link FileIOTask#execute(boolean)} should NOT
 * call any methods of taskExecutor it is submitted into.
 * </p>
 * <p>
 * <b>Error handling</b>: if a task is executed asynchronously, an exception during the task execution can't be delivered to the
 * calling code right away. Instead, all async exceptions are collected and re-thrown together, on the next synchronous
 * {@linkplain #flush()} -- explicit, or implicit (=triggered by synchronous execution of a subsequent task).
 * </p>
 * More details about class behavior are in the implementation comments below.
 */
@ApiStatus.Internal
public final class AsyncableFileIOTaskExecutor<T extends FileIOTaskExecutor.FileIOTask> implements FileIOTaskExecutor<T> {
  private static final Logger LOG = Logger.getInstance(AsyncableFileIOTaskExecutor.class);

  //=========================================================================================================================//
  //The exact behavior of this IOTaskExecutor is not always trivial:
  // 1. Tasks for the same fileId are executed in order of issuing: if .execute( A(fileId=1) ) is called before
  //    .execute( B(fileId=1) ) then task A is guaranteed to be executed before task B, regardless of thread involved.
  //    BEWARE: across different fileIds ordering is NOT preserved!
  //
  // 2. Tasks for the same fileId could be coalesced: if {.execute( A(fileId=1) ), .execute( B(fileId=1) )} are called, and
  //    both tasks allow async execution -- task B _may_ (but not guaranteed to) 'overwrite' task A, so task A is never
  //    executed.
  //    (This is the most primitive version of coalescing, maybe we should develop a more customizable approach)
  //
  // 3. Tasks for the same fileId are never overlap in execution: if task A(fileId=1) has started its execution, other tasks
  //    for the [fileId=1] are guaranteed to not start execution until task A finishes.
  //
  // Subtle complexities around sync/async tasks execution:
  //   - if tasks A(allowAsync=true), B(allowAsync=false) arrive for the same fileId, we can't execute task B immediately, because
  //     this could violate either ordering (1) or non-overlapping (2). Hence, the only way to keep properties (1-2), is for B to
  //     wait for A to finish: if A is already in-progress it means just wait, but if A is still in the queue -- it could 'help'
  //     by executing A on the current thread.
  //     In other words, any task with (allowAsync=false) works as a write-barrier among other task(s) for the same fileId.
  //     (In the current implementation it works as a global write-barrier among all tasks, but this is just a lazy implementation)
  //
  //   - Task could be prohibited from asynchronous execution not only because (allowAsync=false), but also because there are
  //     too many tasks already postponed (=backpressure). In this case the task plays the same write-barrier role, as if it
  //     was (allowAsync=false).
  //
  //   - ...


  private final @NotNull ExecutorService pendingTasksExecutor;

  //@GuardedBy(pendingTasksLock)
  private final @NotNull BackpressureStrategy<? super T> backpressureStrategy;

  /** Tasks waiting for execution */
  //@GuardedBy(pendingTasksLock)
  private final Int2ObjectMap<TaskEntry<T>> pendingTasks = new Int2ObjectOpenHashMap<>();

  /** Tasks taken for execution, but execution is not yet finished */
  //@GuardedBy(pendingTasksLock)
  private final Int2ObjectMap<TaskEntry<T>> inProgressTasks = new Int2ObjectOpenHashMap<>();
  /**
   * [fileId -> Throwable thrown by the _last_ executed task for the fileId].
   * If the last task executed for fileId finished successfully -- this cleans the previous task error, if any.
   */
  //@GuardedBy(pendingTasksLock)
  private final Int2ObjectMap<@NotNull Throwable> lastTaskFailures = new Int2ObjectOpenHashMap<>();

  /**
   * Protects all pending/inProgress/lastTaskFailures collection.
   * Only those collections' queries/updates are under the lock -- the task _execution_ is outside the locked region.
   */
  private transient final Object pendingTasksLock = new Object();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** BEWARE: the executor returned by postponedIOExecutorFactory will be shut down on this class's {@link #close()} */
  public AsyncableFileIOTaskExecutor(@NotNull BackpressureStrategy<? super T> backpressureStrategy,
                                     @NotNull Supplier<? extends ExecutorService> postponedIOExecutorFactory) {
    //TODO RC: should we force executor to be single-threaded? Seems like there is no actual issues with executor having >1 threads
    pendingTasksExecutor = postponedIOExecutorFactory.get();
    if (pendingTasksExecutor == null) {
      throw new IllegalStateException("ioExecutorFactory must not return null (=" + postponedIOExecutorFactory + ")");
    }

    this.backpressureStrategy = backpressureStrategy;
  }

  @Override
  public boolean hasUnfinishedTasksFor(int fileId) {
    synchronized (pendingTasksLock) {
      return pendingTasks.containsKey(fileId) || inProgressTasks.containsKey(fileId);
    }
  }

  @Override
  public @Nullable T unfinishedTaskOrNull(int fileId) {
    synchronized (pendingTasksLock) {
      TaskEntry<T> pendingEntry = pendingTasks.get(fileId);
      if (pendingEntry != null) {
        return pendingEntry.task;
      }
      TaskEntry<T> inProgressEntry = inProgressTasks.get(fileId);
      if (inProgressEntry != null) {
        return inProgressEntry.task;
      }
      return null;
    }
  }

  /**
   * @return (unfinishedTask, null), if there is an unfinished task for fileId, (null, error) if the last task for fileId has finished
   * with error, (null, null) if there is no unfinished task, nor errors for fileId
   */
  public @NotNull Pair<@Nullable T, @Nullable Throwable> unfinishedTaskOrError(int fileId) {
    synchronized (pendingTasksLock) {
      TaskEntry<T> pendingEntry = pendingTasks.get(fileId);
      if (pendingEntry != null) {
        return Pair.pair(pendingEntry.task, null);
      }
      TaskEntry<T> inProgressEntry = inProgressTasks.get(fileId);
      if (inProgressEntry != null) {
        return Pair.pair(inProgressEntry.task, null);
      }
      Throwable error = lastTaskFailures.get(fileId);
      return Pair.pair(null, error);
    }
  }

  @Override
  public boolean hasUnfinishedTasks() {
    synchronized (pendingTasksLock) {
      return !pendingTasks.isEmpty() || !inProgressTasks.isEmpty();
      //RC: inProgressTasks also could contain tasks executed by other thread(s), synchronously -- not only tye postponed tasks
      //    executed by background executor. Hence, this method sometimes returns true when there are no postponed unfinished
      //    tasks, but when some other thread is currently executing some task synchronously.
      //    I think it is harmless, though.
    }
  }

  /**
   * Execute an IO task for the file.
   * <p>
   * The task could be executed immediately or postponed to be executed later -- depending on the conditions like configuration,
   * requestor, size/number of already postponed writes, etc.
   *
   * @return true if the task is postponed to be executed later, false if the task has been executed immediately
   */
  @Override
  public boolean execute(@NotNull T task) throws Exception {
    checkNotClosed();

    int fileId = task.fileId();
    TaskEntry<T> ioTask = new TaskEntry<>(fileId, task);

    boolean needsSynchronousFlush;
    synchronized (pendingTasksLock) {
      int pendingTasksCountBefore = pendingTasks.size();
      TaskEntry<T> previousTask = pendingTasks.put(fileId, ioTask);
      boolean shouldTriggerBackpressure = backpressureStrategy.entering(task);
      if (previousTask != null) {
        backpressureStrategy.exiting(previousTask.task);
      }

      if (shouldTriggerBackpressure) {
        LOG.info(task + ": too much pending work, backoff strategy (" + backpressureStrategy + ") triggers synchronous flush()");
        needsSynchronousFlush = true;
      }
      else if (task.isAsyncExecutionAllowed()) {
        try {
          if (pendingTasksCountBefore == 0) {//do not enqueue new 'flush' task if some 'flush' task is already pending
            pendingTasksExecutor.execute(() -> {
              while (true) {
                executeAllAvailableTasks(/*onBackground: */true);
                synchronized (pendingTasksLock) {
                  if (pendingTasks.isEmpty()) {
                    return;
                  }
                }
              }
            });
            if (LOG.isDebugEnabled()) {
              LOG.debug(task + ": postponed (and async flush() is queued), " +
                        "pendingTasksBefore: " + pendingTasksCountBefore + ", previousTask: " + previousTask);
            }
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug(task + ": postponed (async flush() is already queued), " +
                        "pendingTasksBefore: " + pendingTasksCountBefore + ", previousTask: " + previousTask);
            }
          }
          needsSynchronousFlush = false;
        }
        catch (/*RejectedExecution*/Exception e) {
          LOG.warn(task + ": queueing rejected -> synchronous flush() is forced", e);
          needsSynchronousFlush = true; //assume: queueing is rejected because of overload
        }
      }
      else {
        //TODO RC: actually, if the task prohibits async execution, there is no need to flush _all_ tasks -- only the tasks
        //         for the same fileId must be flushed, everything else is an extra
        needsSynchronousFlush = true;
        if (LOG.isDebugEnabled()) {
          LOG.debug(task + ": prohibits postponed execution -> synchronous flush()");
        }
      }
    }

    if (needsSynchronousFlush) {
      //If some task for the same fileId is already in progress -- a single executeAllAvailableTasks() can't execute the
      // pending ioTask
      // => the statement 'if method returns false => task is executed' could be incorrect.
      // => we need a loop here, waiting for ioTask specifically to be 100% executed:
      while (true) {
        //In theory, we don't need to execute all other pending tasks, only the current one, ioTask. But ioTask _will_ be
        // executed among others anyway if it is not blocked by another in-progress task for the same fileId. And if ioTask's
        // execution _is_ blocked by another task[fileId] currently in progress -- we'll be just waiting here, looping
        // without any utility -- so why not help executing other pending tasks instead?
        executeAllAvailableTasks(/*onBackground: */ false);
        rethrowAndCleanPendingFailures();
        synchronized (pendingTasksLock) {
          if (!pendingTasks.containsValue(ioTask) && !inProgressTasks.containsValue(ioTask)) {
            break;
          }

          pendingTasksLock.wait();//for notifyAll() from .executeIfPending()
        }
      }
    }
    return !needsSynchronousFlush;
  }

  /**
   * Ensures all the tasks that are currently in the executor (postponed or currently running) -- are finished.
   * Does it by executing tasks on the current thread and/or by waiting for other threads to execute them, or
   * mix of both.
   */
  @Override
  public void flush() throws Exception {
    //flush() _could_ be called on closed executor -- it is safe and does nothing, since no new tasks could be added after close

    //The simplest way to define an exit condition is 'no tasks left,' -- but then the loop could run forever if
    // new tasks are added rapidly enough.
    //Instead, the exit condition is defined as 'all tasks that _were in the executor_ at the method start -- are
    // executed'. This way the method is guaranteed to finish in finite time, as long as each individual task
    // finishes in finite time.
    Set<TaskEntry<T>> tasksRemaining;
    synchronized (pendingTasksLock) {
      if (pendingTasks.isEmpty() && inProgressTasks.isEmpty()) {
        rethrowAndCleanPendingFailures();
        return;
      }
      tasksRemaining = new HashSet<>(pendingTasks.values());
      tasksRemaining.addAll(inProgressTasks.values());
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("[flush] tasks to execute: " + tasksRemaining.size());
    }

    while (true) {
      executeAllAvailableTasks(/*onBackground: */ false);

      Cancellation.checkCancelled();

      synchronized (pendingTasksLock) {
        if (pendingTasks.isEmpty() && inProgressTasks.isEmpty()) {
          break;
        }
        //tasksRemaining.retainAll( pending OR inProgress )
        tasksRemaining.removeIf(task -> !pendingTasks.containsValue(task) && !inProgressTasks.containsValue(task));
        if (tasksRemaining.isEmpty()) {
          break;
        }

        pendingTasksLock.wait(ConcurrencyUtil.DEFAULT_TIMEOUT_MS);//for notifyAll() from .executeIfPending()
      }
    }

    totalNonTrivialFlushCalled.incrementAndGet();

    rethrowAndCleanPendingFailures();
  }

  /**
   * Narrow-focused version of {@link #flush()}: ensures all the tasks that are currently in the executor (postponed
   * or currently running) FOR THE fileId -- are finished.
   * Does it by executing tasks on the current thread and/or by waiting for other threads to execute them, or mix
   * of both.
   * Only the exceptions collected from tasks for fileId are rethrown
   */
  public void flush(int fileId) throws Exception {
    TaskEntry<T> pendingEntryToExecute;
    TaskEntry<T> inProgressEntryToWait;
    synchronized (pendingTasksLock) {
      inProgressEntryToWait = inProgressTasks.get(fileId);
      pendingEntryToExecute = pendingTasks.get(fileId);
    }

    while (true) {
      if (pendingEntryToExecute == null && inProgressEntryToWait == null) {
        rethrowAndCleanPendingFailures(fileId);
        return; //nothing more to flush
      }

      synchronized (pendingTasksLock) {
        if (inProgressEntryToWait != null) {
          if (inProgressTasks.containsValue(inProgressEntryToWait)) {
            pendingTasksLock.wait(ConcurrencyUtil.DEFAULT_TIMEOUT_MS);
            continue;
          }
          inProgressEntryToWait = null;
        }

        if (pendingEntryToExecute != null) {
          if (pendingTasks.get(fileId) != pendingEntryToExecute) {       //someone has already taken it for execution (or overwritten it)
            if (inProgressTasks.get(fileId) == pendingEntryToExecute) {  //someone has already _started_ executing it
              inProgressEntryToWait = pendingEntryToExecute;
            }//else: someone has already _finished_ executing it

            pendingEntryToExecute = null;
            continue;
          }
        }
      }

      if (pendingEntryToExecute != null) {
        executeIfPending(pendingEntryToExecute, /*onBackground: */ false);
      }
      rethrowAndCleanPendingFailures(fileId);
    }
  }

  /**
   * Executes the tasks that are currently available for execution -- i.e., those that are pending, but no conflicting tasks
   * for the same fileId are in progress.
   */
  private void executeAllAvailableTasks(boolean onBackground) {
    Set<TaskEntry<T>> tasksToExecute;
    int inProgressTasksCount;
    int pendingTasksCount;
    synchronized (pendingTasksLock) {
      pendingTasksCount = pendingTasks.size();
      if (pendingTasksCount == 0) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("executeAllAvailableTasks(bg: " + onBackground + ") pending: 0 -> nothing to execute");
        }
        return;
      }

      inProgressTasksCount = inProgressTasks.size();

      //tasksToExecute := pendingTask \ inProgressTasks
      tasksToExecute = new HashSet<>(pendingTasks.values());
      tasksToExecute.removeAll(inProgressTasks.values());
    }

    int tasksForExecutionCount = tasksToExecute.size();

    IntRef executedTasks = new IntRef(0);
    tasksToExecute.forEach(taskEntry -> {
      boolean executed = executeIfPending(taskEntry, onBackground);
      if (executed) {
        executedTasks.inc();
      }
    });
    if (LOG.isDebugEnabled()) {
      LOG.debug("executeAllAvailableTasks(bg: " + onBackground + ")" +
                "{pending: " + pendingTasksCount + ", inProgress: " + inProgressTasksCount + "}" +
                " => {attempted execution: " + tasksForExecutionCount + ", executed: " + executedTasks.get() + "}");
    }
  }

  /**
   * @return true if pendingTask was indeed pending, and it was executed (successfully or not),
   * false otherwise: pendingTask wasn't pending (already in progress or finished), or it was pending, but its execution
   * was blocked by another task for the same fileId that is in progress in other thread.
   */
  private boolean executeIfPending(@NotNull TaskEntry<T> pendingTask, boolean onBackground) {
    if (!onBackground) {
      Cancellation.checkCancelled();
    }

    int fileId = pendingTask.fileId;
    synchronized (pendingTasksLock) {
      if (inProgressTasks.containsKey(fileId)) {
        return false;
      }
      boolean wasStillPending = pendingTasks.remove(fileId, pendingTask);
      if (!wasStillPending) {
        return false;
      }

      inProgressTasks.put(fileId, pendingTask);
    }

    long startedAtNs = System.nanoTime();
    Throwable failureInTask = null;
    try {
      try {
        pendingTask.task.execute(onBackground);
      }
      catch (Throwable t) {
        if (!(t instanceof ControlFlowException)) {
          LOG.warn(pendingTask.task + " execution failed", t);
        }
        failureInTask = t;
      }
      return true; //task is finished, regardless of success/failure of it's execution
    }
    finally {
      long finishedAtNs = System.nanoTime();
      synchronized (pendingTasksLock) {
        if (failureInTask != null) {
          lastTaskFailures.put(fileId, failureInTask);
        }
        else {
          lastTaskFailures.remove(fileId);
        }

        inProgressTasks.remove(fileId);
        backpressureStrategy.exiting(pendingTask.task);

        pendingTasksLock.notifyAll();//if someone is waiting for the task blocked by the task just executed
      }
      updateMetrics(onBackground, /*tasks: */ 1, (finishedAtNs - startedAtNs));
    }
  }

  private void rethrowAndCleanPendingFailures() throws Exception {
    Int2ObjectMap<Throwable> failuresCopy;
    synchronized (pendingTasksLock) {
      if (lastTaskFailures.isEmpty()) {
        return;
      }
      failuresCopy = new Int2ObjectOpenHashMap<>(lastTaskFailures);
      lastTaskFailures.clear();
    }

    if (failuresCopy.size() == 1) {
      Throwable soleFailure = failuresCopy.values().iterator().next();
      if (soleFailure instanceof Exception) {
        throw (Exception)soleFailure;
      }
      else if (soleFailure instanceof Error) {
        throw (Error)soleFailure;
      }
      throw new AssertionError("Unrecognized type of Throwable", soleFailure);
    }

    IOException combinedException = new IOException(failuresCopy.size() + " task(s) failed to execute");
    for (Throwable failure : failuresCopy.values()) {
      combinedException.addSuppressed(failure);
    }
    throw combinedException;
  }

  private void rethrowAndCleanPendingFailures(int fileId) throws Exception {
    Throwable failure;
    synchronized (pendingTasksLock) {
      failure = lastTaskFailures.remove(fileId);
    }

    //noinspection ConstantValue (seems like a bug in inspecion)
    if (failure != null) {
      if (failure instanceof Exception) {
        throw (Exception)failure;
      }
      else if (failure instanceof Error) {
        throw (Error)failure;
      }
      throw new AssertionError("Unrecognized type of Throwable", failure);
    }
  }

  @Override
  public void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      pendingTasksExecutor.shutdown();
      flush();//no more tasks could be accepted by writeExecutor after shutdown()
      pendingTasksExecutor.awaitTermination(10, SECONDS);
      //pendingTasksExecutor.shutdownNow();
    }
  }

  private void checkNotClosed() {
    if (closed.get()) {
      throw new IllegalStateException("TaskExecutor is closed");
    }
  }


  //<editor-fold desc="OTel.Metrics"> monitoring counters: =========================================================== //

  //Count stats of all the instances of AsyncableFileIOTaskExecutor, together, along the whole JVM lifetime:

  private static final AtomicLong totalTimeSpentOnSyncOperationsNs = new AtomicLong(0);
  private static final AtomicLong totalTimeSpentOnAsyncOperationsNs = new AtomicLong(0);
  private static final AtomicInteger totalTasksExecutedSync = new AtomicInteger(0);
  private static final AtomicInteger totalTasksExecutedAsync = new AtomicInteger(0);
  /** non-trivial == there was something to flush */
  private static final AtomicInteger totalNonTrivialFlushCalled = new AtomicInteger(0);

  static {
    Meter otelMeter = TelemetryManager.getInstance().getMeter(VFS);
    var totalTasksSyncCounter = otelMeter.counterBuilder("VFS.ContentWriter.totalTasksExecutedSync").buildObserver();
    var totalTasksAsyncCounter = otelMeter.counterBuilder("VFS.ContentWriter.totalTasksExecutedAsync").buildObserver();
    var totalFlushesCounter = otelMeter.counterBuilder("VFS.ContentWriter.totalFlushes").buildObserver();
    var totalTimeSpentSyncUs = otelMeter.counterBuilder("VFS.ContentWriter.totalTimeSpentOnSyncOperationsUs").buildObserver();
    var totalTimeSpentAsyncUs = otelMeter.counterBuilder("VFS.ContentWriter.totalTimeSpentOnAsyncOperationsUs").buildObserver();
    otelMeter.batchCallback(
      () -> {
        totalFlushesCounter.record(totalNonTrivialFlushCalled.get());

        totalTasksSyncCounter.record(totalTasksExecutedSync.get());
        totalTasksAsyncCounter.record(totalTasksExecutedAsync.get());

        totalTimeSpentSyncUs.record(NANOSECONDS.toMicros(totalTimeSpentOnSyncOperationsNs.get()));
        totalTimeSpentAsyncUs.record(NANOSECONDS.toMicros(totalTimeSpentOnAsyncOperationsNs.get()));
      },
      totalTasksSyncCounter, totalTasksAsyncCounter,
      totalTimeSpentSyncUs, totalTimeSpentAsyncUs,
      totalFlushesCounter
    );
  }

  private static void updateMetrics(boolean executedOnBackground,
                                    int tasksExecuted,
                                    long totalTimeSpentNs) {
    if (executedOnBackground) {
      totalTimeSpentOnAsyncOperationsNs.addAndGet(totalTimeSpentNs);
      totalTasksExecutedAsync.addAndGet(tasksExecuted);
    }
    else {
      totalTimeSpentOnSyncOperationsNs.addAndGet(totalTimeSpentNs);
      totalTasksExecutedSync.addAndGet(tasksExecuted);
    }
  }

  //</editor-fold desc="OTel.Metrics">counters end=================================================================== //


  /**
   * Wrapper around an actual IO task.
   * Created so the tasks are always compared by reference, not via .equals().
   * By-reference comparison is crucial for the class's logic -- in this class we always look for a specific task instance,
   * not the task 'essence', whatever it is. The simplest way to ensure by-reference comparison is to always wrap the task
   * into this wrapper -- and this is why it is not a record-class.
   */
  @SuppressWarnings("ClassCanBeRecord")
  private static final class TaskEntry<T extends FileIOTask> {
    public final int fileId;
    public final @NotNull T task;

    private TaskEntry(int fileId, @NotNull T task) {
      this.fileId = fileId;
      this.task = task;
    }

    //equals()/hashCode() are intentionally not overridden -- must be the Object's versions

    @Override
    public String toString() {
      return "TaskEntry[#" + fileId + "](task: " + task + ')';
    }
  }

  /**
   * Limits the amount of postponed activity.
   * If too much activity is postponed, {@link #entering(FileIOTask)} returns true, which signals the executor to create
   * backpressure. The simplest form of backpressure is just flush all postponed tasks synchronously.
   * <p>
   * The contract is:
   * - when executor postpones a task -- the task is 'registered' with the backpressure strategy with {@link #entering(FileIOTask)}
   * - when the task is executed (or substituted by a newer task), it is 'unregistered' with {@link #execute(FileIOTask)}
   */
  public interface BackpressureStrategy<T extends FileIOTask> {
    /** @return true if the incoming task must trigger backpressure */
    boolean entering(@NotNull T task);

    void exiting(@NotNull T task);

    BackpressureStrategy<FileIOTask> NO_BACKPRESSURE = new BackpressureStrategy<>() {
      @Override
      public boolean entering(@NotNull FileIOTask task) { return false; }

      @Override
      public void exiting(@NotNull FileIOTask task) {
      }
    };

    final class ByTasksCount<T extends FileIOTask> implements BackpressureStrategy<T> {
      private final int maxPendingTasksBeforeBackpressure;

      private int pendingTasksCount = 0;

      public ByTasksCount(int maxPendingTasksBeforeBackpressure) {
        this.maxPendingTasksBeforeBackpressure = maxPendingTasksBeforeBackpressure;
      }

      @Override
      public boolean entering(@NotNull T task) {
        pendingTasksCount++;
        return pendingTasksCount > maxPendingTasksBeforeBackpressure;
      }

      @Override
      public void exiting(@NotNull T task) {
        pendingTasksCount--;
      }

      @Override
      public String toString() {
        return "BackpressureByTasksCount[" + pendingTasksCount + " tasks vs " + maxPendingTasksBeforeBackpressure + " max]";
      }
    }
  }
}
