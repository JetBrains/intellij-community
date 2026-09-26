// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.executor;

import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor.BackpressureStrategy.ByTasksCount;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor.BackpressureStrategy.NO_BACKPRESSURE;
import static com.intellij.util.ConcurrencyUtil.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncableFileIOTaskExecutorTest {

  public static final int MAX_PENDING_UPDATES_UNTIL_BACKPRESSURE = 16;

  private ExecutorService executorService;
  private AsyncableFileIOTaskExecutor<AbstractTask> taskExecutor;

  @BeforeEach
  void setUp() {
    executorService = newSingleThreadExecutor("TaskExecutor");
    taskExecutor = new AsyncableFileIOTaskExecutor<>(
      NO_BACKPRESSURE,
      () -> executorService
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    taskExecutor.close();
  }

  private void waitForPendingAsyncFlushToFinish() throws Exception {
    executorService.submit(() -> { }).get(10, SECONDS);
  }


  @Test
  void taskIsPostponed_ifAsync_Is_Allowed() throws Exception {
    int fileId = 1;
    var task = new MockTask(fileId, /*asyncAllowed: */true, /*executeForMs: */ 2_000);
    boolean postponed = taskExecutor.execute(task);

    assertTrue(postponed, "Task should be postponed");
    assertTrue(taskExecutor.hasUnfinishedTasksFor(fileId), "Task should not be finished for ~2sec");
    assertEquals(task, taskExecutor.unfinishedTaskOrNull(fileId), "Task should not be finished for ~2sec");
    assertTrue(taskExecutor.hasUnfinishedTasks(), "Executor should report some unfinished tasks for ~2sec");

    // Wait for execution
    task.waitForExecution(5, SECONDS);

    assertTrue(task.isExecuted(), "Task should be executed");
    assertTrue(task.isExecutedOnBackground(), "Task should be executed on background");
    assertFalse(taskExecutor.hasUnfinishedTasksFor(fileId), "Task should be finished after 5sec");
  }

  @Test
  void taskIsExecutedImmediately_ifAsync_IsNot_Allowed() throws Exception {
    int fileId = 2;
    MockTask task = new MockTask(fileId, /*asyncAllowed: */false, /*executeForMs: */  1_000);

    assertFalse(taskExecutor.execute(task), "Task should NOT be postponed if it doesn't allow it");
    assertFalse(taskExecutor.hasUnfinishedTasksFor(fileId), "Task should be finished after synchronous .execute()");
    assertFalse(taskExecutor.hasUnfinishedTasks(), "Executor should report NO unfinished tasks");
    assertTrue(task.isExecuted(), "Task should be executed (immediately)");
    assertFalse(task.isExecutedOnBackground(), "Task should be executed synchronously");
  }

  @Test
  void tooManyTasksIssued_triggerSynchronousFlush_asBackPressure() throws Exception {
    taskExecutor = new AsyncableFileIOTaskExecutor<>(
      new ByTasksCount<>(MAX_PENDING_UPDATES_UNTIL_BACKPRESSURE),
      () -> executorService
    );
    int tasksToPostpone = 2 * MAX_PENDING_UPDATES_UNTIL_BACKPRESSURE;//x2 just to be sure

    boolean nthTaskIsNotPostponed = false;
    for (int i = 0; i < tasksToPostpone; i++) {
      int fileId = 100 + i;
      MockTask task = new MockTask(fileId, /*asyncAllowed: */ true, /*executeForMs: */ 2_000);
      boolean postponed = taskExecutor.execute(task);
      if (!postponed) {
        nthTaskIsNotPostponed = true;
        break;
      }
    }
    assertTrue(nthTaskIsNotPostponed, "_Some_ N-th task in a row _should_ trigger synchronous flush");
  }

  @Test
  void flush_executesAllUnfinishedTasks() throws Exception {
    List<MockTask> tasks = IntStream.range(1, 5)
      .mapToObj(fileId -> new MockTask(fileId, /*asyncAllowed:*/ true, /*executeForMs: */ 1_000))
      .peek(task -> {
        try {
          taskExecutor.execute(task);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      })
      .toList();

    taskExecutor.flush(); // ~5 sec long

    assertFalse(taskExecutor.hasUnfinishedTasks(), "All previously queued tasks should be finished after .flush()");
    for (MockTask task : tasks) {
      assertTrue(task.isExecuted(), "all tasks should be executed after flush: " + task);
    }
  }

  @Test
  void flushByFileId_executesAllUnfinishedTasks_forFileId() throws Exception {
    MockTask task1 = new MockTask(1, /*asyncAllowed:*/ true, /*executeForMs: */ 5_000);
    MockTask task2 = new MockTask(2, /*asyncAllowed:*/ true, /*executeForMs: */ 1_000);
    MockTask task3 = new MockTask(3, /*asyncAllowed:*/ true, /*executeForMs: */ 7_000);

    taskExecutor.execute(task1);
    taskExecutor.execute(task2);
    taskExecutor.execute(task3);

    taskExecutor.flush(task2.fileId); // ~1 sec wait

    assertTrue(taskExecutor.hasUnfinishedTasks(), "Only task[2] must be finished after .flush(2)");

    assertTrue(taskExecutor.hasUnfinishedTasksFor(task1.fileId), "task[1] should be still running");
    assertFalse(task1.isExecuted(), "task[1] should be still running");

    assertTrue(taskExecutor.hasUnfinishedTasksFor(task3.fileId), "task[3] should be still running");
    assertFalse(task3.isExecuted(), "task[3] should be still running");
  }


  @Test
  void hasUnfinishedTasksFor_ReturnsTrue_ForInProgressTasks() throws Exception {
    AtomicInteger started = new AtomicInteger(0);
    int fileId = 4;
    MockTask task = new MockTask(fileId, true, /*executeForMs: */ 5_000) {
      @Override
      public void execute(boolean executedOnBackground) throws Exception {
        started.incrementAndGet();
        super.execute(executedOnBackground);
      }
    };

    taskExecutor.execute(task);

    // Wait until it starts
    long deadline = System.currentTimeMillis() + 5000;
    while (started.get() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertEquals(1, started.get(), "Task should start in <= 5sec");

    assertTrue(taskExecutor.hasUnfinishedTasksFor(fileId), "Task should be 'unfinished' while in progress");
    assertTrue(taskExecutor.hasUnfinishedTasks(), "Executor should report unfinished task while task is executed");

    task.waitForExecution(5, SECONDS);
    assertFalse(taskExecutor.hasUnfinishedTasksFor(fileId), "NO unfinished tasks should be reported after task is executed");
  }

  @Test
  void executeFails_afterClose() throws Exception {
    taskExecutor.close();
    assertThrows(
      IllegalStateException.class,
      () -> taskExecutor.execute(new MockTask(1, true))
    );
  }

  @Test
  void flushDoesNotFail_afterClose() throws Exception {
    taskExecutor.close();
    taskExecutor.flush();//doesn't throw, just does nothing
  }


  @Test
  void concurrentlyExecutedWrites_areNeverOverlapInExecution_andAppliedInOrderOfIssuing() {
    //Test multithreaded behavior:
    // - start {writersCount} writers, each issues {writesPerWriter} tasks to write to [0..filesCount) 'files'
    // - each 'write' marks start/finish with some pause in between
    // - each 'write' checks it does NOT overlap with another write
    // - at the end check that the last write issued is the last write applied -- i.e. taskExecutor keeps order of tasks

    int filesCount = 32;// not too much to increase contention
    int writersCount = Runtime.getRuntime().availableProcessors();
    int writesPerWriter = 1024;

    AtomicIntegerArray filesContentGenerator = new AtomicIntegerArray(filesCount);
    AtomicIntegerArray filesContent = new AtomicIntegerArray(filesCount);

    try (var pool = Executors.newFixedThreadPool(writersCount)) {
      for (int writerNo = 0; writerNo < writersCount; writerNo++) {
        int _writerNo = writerNo;
        //noinspection SSBasedInspection
        pool.submit((Callable<Void>)() -> {
          ThreadLocalRandom rnd = ThreadLocalRandom.current();
          for (int writeNo = 0; writeNo < writesPerWriter; writeNo++) {
            int _writeNo = writeNo;
            int fileId = rnd.nextInt(filesCount);
            int fileContent = filesContentGenerator.incrementAndGet(fileId);
            //assume: odd number = (task started), even number = (task finished)
            int unfinishedWriteMarker = fileContent * 2 + 1;
            int finishedWriteMarker = fileContent * 2;

            taskExecutor.execute(new AbstractTask(fileId, /*asyncAllowed: */ true) {
              @Override
              public void execute(boolean executedOnBackground) {
                int currentValue = filesContent.getAndSet(fileId, unfinishedWriteMarker);
                if ((currentValue % 2) != 0) {
                  fail("writer[#" + _writerNo + "][write: " + _writeNo + "]{fileId: #" + fileId + " = " + currentValue + "} " +
                       "overlapping (concurrent) write detected");
                }
                Thread.yield();//emulate 'prolonged' task execution time
                filesContent.getAndSet(fileId, finishedWriteMarker);
              }
            });
          }
          return null;
        });
      }
    }

    //check that taskExcutor keeps writes order (for given fileId): i.e,  writes that are issued later -- are applied later
    //TODO RC: not strictly true because lastContentGenerated != the last task issued -- there is a time window between
    //         content generation and issuing the Task into taskExecutor.
    //for (int fileId = 0; fileId < filesCount; fileId++) {
    //  int lastContentGenerated = filesContentGenerator.get(fileId) * 2;
    //  int lastContentWritten = filesContent.get(fileId);
    //  assertEquals(lastContentGenerated, lastContentWritten,
    //               "[fileId: " + fileId + "] non-last write wins");
    //}
  }

  //TODO RC: check backpressure strategy works correctly under many tasks executed sync/async


  @Test
  void exception_inSyncExecution_isPropagatedDirectly() throws Exception {
    int fileId = 43;
    Exception failure = new Exception();
    try {
      taskExecutor.execute(new MockTask(fileId, /*async:*/false, failure));
      fail("Synchronous execution should directly propagate the exception thrown by the task");
    }
    catch (Exception e) {
      assertEquals(failure, e, "Should directly propagate the exception thrown by the task");
    }
  }

  @Test
  void exception_inAsyncExecution_isRethrownOnFlush() throws Exception {
    int fileId = 43;
    Exception failure = new Exception();
    //shouldn't throw because the task is async:
    taskExecutor.execute(new MockTask(fileId, /*async:*/true, failure));
    try {
      taskExecutor.flush();
      fail("Flushing should rethrow the exception thrown by the task");
    }
    catch (Exception e) {
      assertEquals(failure, e, "Single exception -> should be propagated directly, without any wrapping");
    }
  }

  @Test
  void exception_inAsyncExecution_isRethrownOnFlush_ifTaskAlreadyFinished() throws Exception {
    int fileId = 43;
    Exception failure = new Exception();
    taskExecutor.execute(new MockTask(fileId, /*async:*/true, failure));
    waitForPendingAsyncFlushToFinish();

    Exception e = assertThrows(Exception.class, () -> taskExecutor.flush());
    assertEquals(failure, e, "Single exception from already finished async task should still be propagated");
  }

  @Test
  void exceptions_inAsyncExecution_areRethrownOnFlush_combined() throws Exception {
    Exception failure1 = new Exception("Failure#1");
    Exception failure2 = new Exception("Failure#2");
    Exception failure3 = new Exception("Failure#3");

    //shouldn't throw because the tasks are async:
    taskExecutor.execute(new MockTask(/*fileId: */ 41, /*async:*/ true, failure1));
    taskExecutor.execute(new MockTask(/*fileId: */ 42, /*async:*/ true, failure2));
    taskExecutor.execute(new MockTask(/*fileId: */ 43, /*async:*/ true, failure3));
    try {
      taskExecutor.flush();
      fail("flush() should rethrow the exceptions thrown by the tasks");
    }
    catch (Exception e) {
      assertEquals(
        Set.of(e.getSuppressed()),
        Set.of(failure1, failure2, failure3),
        "3 failed tasks -> 3 exceptions should be in a suppressed chain"
      );
    }
  }

  @Test
  void exception_inAsyncExecution_areRethrownOnFlushByFileId_onlyForGivenFileId() throws Exception {
    Exception failure1 = new Exception("Failure#1");
    Exception failure2 = new Exception("Failure#2");
    Exception failure3 = new Exception("Failure#3");
    MockTask task1 = new MockTask(/*fileId: */ 41, /*async:*/ true, failure1);
    MockTask task2 = new MockTask(/*fileId: */ 42, /*async:*/ true, failure2);
    MockTask task3 = new MockTask(/*fileId: */ 43, /*async:*/ true, failure3);

    //shouldn't throw because the tasks are async:
    taskExecutor.execute(task1);
    taskExecutor.execute(task2);
    taskExecutor.execute(task3);

    try {
      taskExecutor.flush(task2.fileId);
      fail("flush(fileId) should rethrow the exception thrown by the task(fileId)");
    }
    catch (Exception e) {
      assertEquals(
        e,
        failure2,
        "3 failed tasks, but only failure(fileId=42) should be rethrown"
      );
    }

    try {
      taskExecutor.flush();
    }
    catch (Exception ignored) {
      //clean the exceptions so they don't ruin tearDown()
    }
  }

  @Test
  void exception_inAsyncExecution_isRethrownOnFlushByFileId_ifTaskAlreadyFinished() throws Exception {
    Exception failure1 = new Exception("Failure#1");
    Exception failure2 = new Exception("Failure#2");
    Exception failure3 = new Exception("Failure#3");

    taskExecutor.execute(new MockTask(/*fileId: */ 41, /*async:*/ true, failure1));
    taskExecutor.execute(new MockTask(/*fileId: */ 42, /*async:*/ true, failure2));
    taskExecutor.execute(new MockTask(/*fileId: */ 43, /*async:*/ true, failure3));
    waitForPendingAsyncFlushToFinish();

    Exception e = assertThrows(Exception.class, () -> taskExecutor.flush(42));
    assertEquals(failure2, e, "Only failure(fileId=42) should be rethrown");

    try {
      taskExecutor.flush();
    }
    catch (Exception ignored) {
      //clean the exceptions so they don't ruin tearDown()
    }
  }

  @Test
  void exceptions_inAsyncExecution_onlyTheLastPerFileId_IsRethrown() throws Exception {
    Exception failure1 = new Exception("Failure#1");
    Exception failure2 = new Exception("Failure#2");
    Exception failure3 = new Exception("Failure#3");

    //shouldn't throw because the tasks are async:
    int fileId = 41;
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure1));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure2));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure3));
    try {
      taskExecutor.flush();
      fail("flush() should rethrow the exception thrown by the last task");
    }
    catch (Exception e) {
      assertEquals(
        failure3,
        e,
        "Only the last exception for fileId is rethrown, previous exceptions for the same fileId are overwritten"
      );
    }
  }

  @Test
  void exceptions_inAsyncExecution_theLastSuccessfulTaskResult_clearsTheExceptions() throws Exception {
    Exception failure1 = new Exception("Failure#1");
    Exception failure2 = new Exception("Failure#2");
    Exception failure3 = new Exception("Failure#3");

    //shouldn't throw because the tasks are async:
    int fileId = 41;
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure1));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure2));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, failure3));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true /*successful*/));

    //Should not fail, since the last task for fileId is successful => must override the previously failing result(s):
    taskExecutor.flush();
  }

  @Test
  void exceptions_inAsyncExecution_areClearedAfterBeingRethrownOnce() throws Exception {
    //shouldn't throw because the tasks are async:
    int fileId = 41;
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, () -> new Exception("Failure#1")));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, () -> new Exception("Failure#2")));
    taskExecutor.execute(new MockTask(fileId, /*async:*/ true, () -> new Exception("Failure#3")));
    try {
      taskExecutor.flush();
      fail("flush() should rethrow the exception(s) thrown by the task(s)");
    }
    catch (Exception ignore) {
    }

    //Second flush should NOT throw anything -- the first flush() clears collected exceptions:
    taskExecutor.flush();
  }


  private static abstract class AbstractTask implements FileIOTaskExecutor.FileIOTask {
    protected final int fileId;
    protected final boolean asyncAllowed;

    protected AbstractTask(int fileId, boolean asyncAllowed) {
      this.fileId = fileId;
      this.asyncAllowed = asyncAllowed;
    }

    @Override
    public final int fileId() {
      return fileId;
    }

    @Override
    public final boolean isAsyncExecutionAllowed() {
      return asyncAllowed;
    }
  }

  private static class MockTask extends AbstractTask {

    private volatile boolean executed = false;
    private volatile boolean executedOnBackground = false;

    private final Supplier<Exception> failWith;
    private final long executeForMs;

    private final Object executionLock = new Object();

    MockTask(int fileId, boolean asyncAllowed) {
      this(fileId, asyncAllowed, /*failWith: */null, -1);
    }

    MockTask(int fileId, boolean asyncAllowed, long executeForMs) {
      this(fileId, asyncAllowed, /*failWith: */null, executeForMs);
    }

    MockTask(int fileId, boolean asyncAllowed, @Nullable Supplier<Exception> failWith) {
      this(fileId, asyncAllowed, failWith, -1);
    }

    MockTask(int fileId, boolean asyncAllowed, @Nullable Exception failWith) {
      this(fileId, asyncAllowed, failWith == null ? null : () -> failWith, -1);
    }

    private MockTask(int fileId, boolean asyncAllowed, @Nullable Supplier<Exception> failWith, long executeForMs) {
      super(fileId, asyncAllowed);
      this.failWith = failWith;
      this.executeForMs = executeForMs;
    }

    @Override
    public void execute(boolean executedOnBackground) throws Exception {
      if (failWith != null) {
        throw failWith.get();
      }
      if (executeForMs > 0) {
        try {
          Thread.sleep(executeForMs);
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      synchronized (executionLock) {
        this.executed = true;
        this.executedOnBackground = executedOnBackground;
        executionLock.notifyAll();
      }
    }

    public boolean isExecuted() {
      return executed;
    }

    public boolean isExecutedOnBackground() {
      return executedOnBackground;
    }

    public void waitForExecution(long timeout, TimeUnit unit) throws InterruptedException {
      synchronized (executionLock) {
        while (!executed) {
          executionLock.wait(unit.toMillis(timeout));
        }
      }
    }

    @Override
    public String toString() {
      return "MockTask{fileId=" + fileId + ", asyncAllowed=" + asyncAllowed + ", executeForMs=" + executeForMs + "}" +
             "{executed=" + executed + ", executedOnBackground=" + executedOnBackground + '}';
    }
  }
}
