// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.TestVcsLogProvider;
import com.intellij.vcs.log.impl.TimedVcsCommitImpl;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.test.VcsPlatformTest;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

import static com.intellij.vcs.log.TimedCommitParser.log;

public class VcsLogRefresherTest extends VcsPlatformTest {
  private static final Logger LOG = Logger.getInstance(VcsLogRefresherTest.class);

  private static final int RECENT_COMMITS_COUNT = 2;
  private TestVcsLogProvider myLogProvider;
  private VcsLogData myLogData;
  private Map<VirtualFile, VcsLogProvider> myLogProviders;

  private DataWaiter myDataWaiter;
  private VcsLogRefresher myLoader;
  private final List<Future<?>> myStartedTasks = Collections.synchronizedList(new ArrayList<>());

  private List<String> myCommits;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myLogProvider = new TestVcsLogProvider();
    myLogProviders = Collections.singletonMap(getProjectRoot(), myLogProvider);

    myCommits = Arrays.asList("3|-a2|-a1", "2|-a1|-a", "1|-a|-");
    myLogProvider.appendHistory(log(myCommits));
    myLogProvider.addRef(createBranchRef("master", "a2"));

    myDataWaiter = new DataWaiter();
    myLoader = createLoader(myDataWaiter);
  }

  @Override
  public void tearDown() {
    try {
      assertNoMoreResultsArrive();
      myDataWaiter.tearDown();
      if (myDataWaiter.failed()) {
        fail("Only one refresh should have happened, an error happened instead: " + myDataWaiter.getExceptionText());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected @NotNull Collection<String> getDebugLogCategories() {
    return Arrays.asList("#" + SingleTaskController.class.getName(), "#" + VcsLogRefresherImpl.class.getName(),
                         "#" + VcsLogRefresherTest.class.getName(), "#" + TestVcsLogProvider.class.getName());
  }

  public void test_initialize_shows_short_history() throws InterruptedException, ExecutionException, TimeoutException {
    myLogProvider.blockFullLog();
    myLoader.readFirstBlock();
    DataPack result = myLoader.getCurrentDataPack();
    myLogProvider.unblockFullLog();
    assertNotNull(result);
    assertDataPack(log(myCommits.subList(0, 2)), result.getPermanentGraph().getAllCommits());
    waitForBackgroundTasksToComplete();
    myDataWaiter.get();
  }

  public void test_first_refresh_reports_full_history() throws InterruptedException {
    myLoader.readFirstBlock();

    DataPack result = myDataWaiter.get();
    assertDataPack(log(myCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_first_refresh_waits_for_full_log() throws InterruptedException {
    myLogProvider.blockFullLog();
    myLoader.readFirstBlock();
    assertTimeout("Refresh waiter should have failed on the timeout");
    myLogProvider.unblockFullLog();

    DataPack result = myDataWaiter.get();
    assertDataPack(log(myCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_refresh_captures_new_commits() throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();

    String newCommit = "4|-a3|-a2";
    myLogProvider.appendHistory(log(newCommit));
    myLoader.refresh(Collections.singletonList(getProjectRoot()));
    DataPack result = myDataWaiter.get();

    List<String> allCommits = new ArrayList<>();
    allCommits.add(newCommit);
    allCommits.addAll(myCommits);
    assertDataPack(log(allCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_single_refresh_causes_single_data_read() throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();

    myLogProvider.resetReadFirstBlockCounter();
    myLoader.refresh(Collections.singletonList(getProjectRoot()));
    myDataWaiter.get();
    assertEquals("Unexpected first block read count", 1, myLogProvider.getReadFirstBlockCounter());
  }

  public void test_reinitialize_makes_refresh_cancelled() throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();

    // initiate the refresh and make it hang
    myLogProvider.blockRefresh();
    myLoader.refresh(Collections.singletonList(getProjectRoot()));

    // initiate reinitialize; the full log will await because the Task is busy waiting for the refresh
    myLoader.readFirstBlock();

    // the task queue now contains (1) blocked ongoing refresh request; (2) queued complete refresh request
    // we want to make sure only one data pack is reported
    myLogProvider.unblockRefresh();
    myDataWaiter.get();
    assertNoMoreResultsArrive();
  }

  private void assertNoMoreResultsArrive() throws InterruptedException, ExecutionException, TimeoutException {
    waitForBackgroundTasksToComplete();
    assertTrue(myDataWaiter.myQueue.isEmpty());
  }

  private void waitForBackgroundTasksToComplete() throws InterruptedException, ExecutionException, TimeoutException {
    for (Future<?> task : new ArrayList<>(myStartedTasks)) {
      task.get(1, TimeUnit.SECONDS);
    }
  }

  public void test_two_immediately_consecutive_refreshes_causes_only_one_data_pack_update()
    throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();
    myLogProvider.blockRefresh();
    myLoader.refresh(Collections.singletonList(getProjectRoot())); // this refresh hangs in VcsLogProvider.readFirstBlock()
    myLoader.refresh(Collections.singletonList(getProjectRoot())); // this refresh is queued
    myLogProvider.unblockRefresh(); // this will make the first one complete, and then perform the second as well

    myDataWaiter.get();
    assertTimeout("Second refresh shouldn't cause the data pack update"); // it may also fail in beforehand in set().
  }

  private void initAndWaitForFirstRefresh() throws InterruptedException, ExecutionException, TimeoutException {
    // wait for the first block and the whole log to complete
    myLoader.readFirstBlock();
    DataPack fullDataPack = myDataWaiter.get();
    assertTrue(fullDataPack.isFull());
    assertNoMoreResultsArrive();
  }

  private void assertTimeout(@NotNull String message) throws InterruptedException {
    assertNull(message, myDataWaiter.myQueue.poll(500, TimeUnit.MILLISECONDS));
  }

  private VcsLogRefresherImpl createLoader(Consumer<? super DataPack> dataPackConsumer) {
    myLogData = new VcsLogData(myProject, myLogProviders, new LoggingErrorHandler(LOG), myProject);
    VcsLogRefresherImpl refresher =
      new VcsLogRefresherImpl(myProject, myLogData.getStorage(), myLogProviders, myLogData.getUserRegistry(),
                              myLogData.getModifiableIndex(),
                              new VcsLogProgress(myLogData),
                              myLogData.getTopCommitsCache(), dataPackConsumer, RECENT_COMMITS_COUNT
      ) {
        @Override
        protected SingleTaskController.SingleTask startNewBackgroundTask(final @NotNull Task.Backgroundable refreshTask) {
          LOG.debug("Starting a background task...");
          Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(refreshTask);
          myStartedTasks.add(future);
          LOG.debug(myStartedTasks.size() + " started tasks");
          return new SingleTaskController.SingleTaskImpl(future, new EmptyProgressIndicator());
        }
      };
    Disposer.register(myLogData, refresher);
    return refresher;
  }

  private void assertDataPack(@NotNull List<TimedVcsCommit> expectedLog, @NotNull List<? extends GraphCommit<Integer>> actualLog) {
    List<TimedVcsCommit> convertedActualLog = convert(actualLog);
    assertOrderedEquals(convertedActualLog, expectedLog);
  }

  private @NotNull List<TimedVcsCommit> convert(@NotNull List<? extends GraphCommit<Integer>> actualLog) {
    return ContainerUtil.map(actualLog, commit -> {
      Function<Integer, Hash> convertor = integer -> myLogData.getCommitId(integer).getHash();
      return new TimedVcsCommitImpl(convertor.fun(commit.getId()), ContainerUtil.map(commit.getParents(), convertor),
                                    commit.getTimestamp());
    });
  }

  private @NotNull VcsRefImpl createBranchRef(@NotNull String name, @NotNull String commit) {
    return new VcsRefImpl(HashImpl.build(commit), name, TestVcsLogProvider.BRANCH_TYPE, getProjectRoot());
  }

  private static class DataWaiter implements Consumer<DataPack> {
    private volatile BlockingQueue<DataPack> myQueue = new ArrayBlockingQueue<>(10);
    private volatile Exception myException;

    @Override
    public void consume(DataPack t) {
      try {
        myQueue.add(t);
      }
      catch (Exception e) {
        myException = e;
        throw new AssertionError(e);
      }
    }

    public @NotNull DataPack get(long timeout, @NotNull TimeUnit timeUnit) throws InterruptedException {
      return Objects.requireNonNull(myQueue.poll(timeout, timeUnit));
    }

    public boolean failed() {
      return myException != null;
    }

    String getExceptionText() {
      return ExceptionUtil.getThrowableText(myException);
    }

    public @NotNull DataPack get() throws InterruptedException {
      return get(1, TimeUnit.SECONDS);
    }

    public void tearDown() {
      myQueue = null;
    }
  }
}
