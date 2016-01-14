/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.impl.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

import static com.intellij.vcs.log.TimedCommitParser.log;

public class VcsLogRefresherTest extends VcsLogPlatformTest {

  private static final Logger LOG = Logger.getInstance(VcsLogRefresherTest.class);

  private static final int RECENT_COMMITS_COUNT = 2;
  public static final Consumer<Exception> FAILING_EXCEPTION_HANDLER = new Consumer<Exception>() {
    @Override
    public void consume(@NotNull Exception e) {
      throw new AssertionError(e);
    }
  };
  private TestVcsLogProvider myLogProvider;
  private VcsLogDataHolder myDataHolder;
  private Map<Integer, VcsCommitMetadata> myTopDetailsCache;
  private Map<VirtualFile, VcsLogProvider> myLogProviders;

  private DataWaiter myDataWaiter;
  private VcsLogRefresher myLoader;
  private List<Future<?>> myStartedTasks;

  private List<String> myCommits;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myLogProvider = new TestVcsLogProvider(myProjectRoot);
    myLogProviders = Collections.<VirtualFile, VcsLogProvider>singletonMap(myProjectRoot, myLogProvider);
    myTopDetailsCache = ContainerUtil.newHashMap();

    myCommits = Arrays.asList("3|-a2|-a1", "2|-a1|-a", "1|-a|-");
    myLogProvider.appendHistory(log(myCommits));
    myLogProvider.addRef(createBranchRef("master", "a2"));

    myStartedTasks = new ArrayList<Future<?>>();
    myDataWaiter = new DataWaiter();
    myLoader = createLoader(myDataWaiter);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      assertNoMoreResultsArrive();
      myDataWaiter.tearDown();
      if (myDataWaiter.failed()) {
        fail("Only one refresh should have happened, an error happened instead: " + myDataWaiter.getExceptionText());
      }
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected Collection<String> getDebugLogCategories() {
    return Arrays.asList("#" + SingleTaskController.class.getName(),
                         "#" + VcsLogRefresherImpl.class.getName(),
                         "#" + VcsLogRefresherTest.class.getName(),
                         "#" + TestVcsLogProvider.class.getName());
  }

  public void test_initialize_shows_short_history() throws InterruptedException, ExecutionException, TimeoutException {
    DataPack result = myLoader.readFirstBlock();
    assertNotNull(result);
    assertDataPack(log(myCommits.subList(0, 2)), result.getPermanentGraph().getAllCommits());
    waitForBackgroundTasksToComplete();
    myDataWaiter.get();
  }

  public void test_first_refresh_reports_full_history() throws InterruptedException, ExecutionException, TimeoutException {
    myLoader.readFirstBlock();

    DataPack result = myDataWaiter.get();
    assertDataPack(log(myCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_first_refresh_waits_for_full_log() throws InterruptedException, ExecutionException, TimeoutException {
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
    myLoader.refresh(Collections.singletonList(myProjectRoot));
    DataPack result = myDataWaiter.get();

    List<String> allCommits = ContainerUtil.newArrayList();
    allCommits.add(newCommit);
    allCommits.addAll(myCommits);
    assertDataPack(log(allCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_single_refresh_causes_single_data_read() throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();

    myLogProvider.resetReadFirstBlockCounter();
    myLoader.refresh(Collections.singletonList(myProjectRoot));
    myDataWaiter.get();
    assertEquals("Unexpected first block read count", 1, myLogProvider.getReadFirstBlockCounter());
  }

  public void test_reinitialize_makes_refresh_cancelled() throws InterruptedException, ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();

    // initiate the refresh and make it hang
    myLogProvider.blockRefresh();
    myLoader.refresh(Collections.singletonList(myProjectRoot));

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
    for (Future<?> task : myStartedTasks) {
      task.get(1, TimeUnit.SECONDS);
    }
  }

  public void test_two_immediately_consecutive_refreshes_causes_only_one_data_pack_update() throws InterruptedException,
                                                                                                   ExecutionException, TimeoutException {
    initAndWaitForFirstRefresh();
    myLogProvider.blockRefresh();
    myLoader.refresh(Collections.singletonList(myProjectRoot)); // this refresh hangs in VcsLogProvider.readFirstBlock()
    myLoader.refresh(Collections.singletonList(myProjectRoot)); // this refresh is queued
    myLogProvider.unblockRefresh(); // this will make the first one complete, and then perform the second as well

    myDataWaiter.get();
    assertTimeout("Second refresh shouldn't cause the data pack update"); // it may also fail in beforehand in set().
  }

  private void initAndWaitForFirstRefresh()
    throws InterruptedException, ExecutionException, TimeoutException
  {
    // wait for the first block and the whole log to complete
    myLoader.readFirstBlock();
    DataPack fullDataPack = myDataWaiter.get();
    assertTrue(fullDataPack.isFull());
    assertNoMoreResultsArrive();
  }

  private void assertTimeout(@NotNull String message) throws InterruptedException, ExecutionException {
    assertNull(message, myDataWaiter.myQueue.poll(500, TimeUnit.MILLISECONDS));
  }

  private VcsLogRefresherImpl createLoader(Consumer<DataPack> dataPackConsumer) {
    myDataHolder = new VcsLogDataHolder(myProject, myProject, myLogProviders,
                                                       ServiceManager.getService(myProject, VcsLogSettings.class),
                                                       ServiceManager.getService(myProject, VcsLogUiProperties.class), Consumer.EMPTY_CONSUMER);
    return new VcsLogRefresherImpl(myProject, myDataHolder.getHashMap(), myLogProviders, myDataHolder.getUserRegistry(), myTopDetailsCache,
                                   dataPackConsumer, FAILING_EXCEPTION_HANDLER, RECENT_COMMITS_COUNT) {
      @Override
      protected void startNewBackgroundTask(@NotNull final Task.Backgroundable refreshTask) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            LOG.debug("Starting a background task...");
            myStartedTasks.add(((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(refreshTask));
            LOG.debug(myStartedTasks.size() + " started tasks");
          }
        });
      }
    };
  }

  private void assertDataPack(@NotNull List<TimedVcsCommit> expectedLog, @NotNull List<GraphCommit<Integer>> actualLog) {
    List<TimedVcsCommit> convertedActualLog = convert(actualLog);
    assertOrderedEquals(convertedActualLog, expectedLog);
  }

  @NotNull
  private List<TimedVcsCommit> convert(@NotNull List<GraphCommit<Integer>> actualLog) {
    return ContainerUtil.map(actualLog, new Function<GraphCommit<Integer>, TimedVcsCommit>() {
      @NotNull
      @Override
      public TimedVcsCommit fun(@NotNull GraphCommit<Integer> commit) {
        Function<Integer, Hash> convertor = new Function<Integer, Hash>() {
          @NotNull
          @Override
          public Hash fun(Integer integer) {
            return myDataHolder.getCommitId(integer).getHash();
          }
        };
        return new TimedVcsCommitImpl(convertor.fun(commit.getId()), ContainerUtil.map(commit.getParents(), convertor),
                                      commit.getTimestamp());
      }
    });
  }

  @NotNull
  private VcsRefImpl createBranchRef(@NotNull String name, @NotNull String commit) {
    return new VcsRefImpl(HashImpl.build(commit), name, TestVcsLogProvider.BRANCH_TYPE, myProjectRoot);
  }

  private static class DataWaiter implements Consumer<DataPack> {
    private volatile BlockingQueue<DataPack> myQueue = new ArrayBlockingQueue<DataPack>(10);
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

    @NotNull
    public DataPack get(long timeout, @NotNull TimeUnit timeUnit) throws InterruptedException {
      return ObjectUtils.assertNotNull(myQueue.poll(timeout, timeUnit));
    }

    public boolean failed() {
      return myException != null;
    }

    public String getExceptionText() {
      return ExceptionUtil.getThrowableText(myException);
    }

    @NotNull
    public DataPack get() throws InterruptedException {
      return get(1, TimeUnit.SECONDS);
    }

    public void tearDown() {
      myQueue = null;
    }
  }
}
