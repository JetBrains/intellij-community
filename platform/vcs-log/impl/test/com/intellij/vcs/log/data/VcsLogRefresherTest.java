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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.impl.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.vcs.log.TimedCommitParser.log;

public class VcsLogRefresherTest extends VcsLogPlatformTest {

  private static final int RECENT_COMMITS_COUNT = 2;
  public static final Consumer<Exception> FAILING_EXCEPTION_HANDLER = new Consumer<Exception>() {
    @Override
    public void consume(@NotNull Exception e) {
      throw new AssertionError(e);
    }
  };
  @NotNull private TestVcsLogProvider myLogProvider;
  @NotNull private VcsLogDataHolder myDataHolder;
  @NotNull private Map<Hash, VcsCommitMetadata> myTopDetailsCache;
  @NotNull private Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private List<String> myCommits;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myLogProvider = new TestVcsLogProvider(myProjectRoot);
    myLogProviders = Collections.<VirtualFile, VcsLogProvider>singletonMap(myProjectRoot, myLogProvider);
    myTopDetailsCache = new HashMap<Hash, VcsCommitMetadata>();

    myCommits = Arrays.asList("3|-a2|-a1", "2|-a1|-a", "1|-a|-");
    myLogProvider.appendHistory(log(myCommits));
    myLogProvider.addRef(createBranchRef("master", "a2"));
  }

  public void test_initialize_shows_short_history() throws InterruptedException, ExecutionException, TimeoutException {
    VcsLogRefresher loader = createLoader(new DataWaiter());

    DataPack result = loader.readFirstBlock();
    assertNotNull(result);
    assertDataPack(log(myCommits.subList(0, 2)), result.getPermanentGraph().getAllCommits());
  }

  public void test_first_refresh_reports_full_history() throws InterruptedException, ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);
    loader.readFirstBlock();

    DataPack result = dataWaiter.get(1, TimeUnit.SECONDS);
    assertNotNull(result);
    assertDataPack(log(myCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_first_refresh_waits_for_full_log() throws InterruptedException, ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);

    myLogProvider.blockFullLog();
    loader.readFirstBlock();
    assertTimeout(dataWaiter, "Refresh waiter should have failed on the timeout");
    myLogProvider.unblockFullLog();

    DataPack result = dataWaiter.get(1, TimeUnit.SECONDS);
    assertNotNull(result);
    assertDataPack(log(myCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_refresh_captures_new_commits() throws InterruptedException, ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);
    initAndWaitForFirstRefresh(dataWaiter, loader);

    String newCommit = "4|-a3|-a2";
    myLogProvider.appendHistory(log(newCommit));
    loader.refresh(Collections.singletonList(myProjectRoot));
    DataPack result = dataWaiter.get(1, TimeUnit.SECONDS);
    assertNotNull(result);
    List<String> allCommits = ContainerUtil.newArrayList();
    allCommits.add(newCommit);
    allCommits.addAll(myCommits);
    assertDataPack(log(allCommits), result.getPermanentGraph().getAllCommits());
  }

  public void test_single_refresh_causes_single_data_read() throws InterruptedException, ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);
    initAndWaitForFirstRefresh(dataWaiter, loader);

    myLogProvider.resetReadFirstBlockCounter();
    loader.refresh(Collections.singletonList(myProjectRoot));
    dataWaiter.get(1, TimeUnit.SECONDS);
    assertEquals("Unexpected first block read count", 1, myLogProvider.getReadFirstBlockCounter());
  }

  public void test_reinitialize_cancels_refresh() throws InterruptedException, ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);
    loader.readFirstBlock();
    myLogProvider.blockRefresh();
    loader.refresh(Collections.singletonList(myProjectRoot));
    loader.readFirstBlock();
    myLogProvider.unblockRefresh();

    dataWaiter.get(1, TimeUnit.SECONDS);
    assertFalse("Only one refresh should have happened", dataWaiter.failed());
  }

  public void test_two_immediately_consecutive_refreshes_causes_only_one_data_pack_update() throws InterruptedException,
                                                                                                   ExecutionException, TimeoutException {
    DataWaiter dataWaiter = new DataWaiter();
    VcsLogRefresher loader = createLoader(dataWaiter);

    myLogProvider.blockRefresh(); // make sure the first refresh won't complete before the second is queued.
    loader.readFirstBlock();
    loader.refresh(Collections.singletonList(myProjectRoot));
    myLogProvider.unblockRefresh();

    dataWaiter.get(1, TimeUnit.SECONDS);
    dataWaiter.reset();
    assertTimeout(dataWaiter, "Second refresh shouldn't cause the data pack update"); // it may also fail in beforehand in set().
    assertFalse("DataPack FutureResult failed with an exception", dataWaiter.failed());
  }

  private static void initAndWaitForFirstRefresh(DataWaiter dataWaiter, VcsLogRefresher loader)
    throws InterruptedException, ExecutionException, TimeoutException {
    loader.readFirstBlock();
    dataWaiter.get(1, TimeUnit.SECONDS); // wait for the first refresh to complete
    dataWaiter.reset();
  }

  private static void assertTimeout(@NotNull DataWaiter dataWaiter, @NotNull String message)
    throws InterruptedException, ExecutionException {
    boolean timeout = false;
    try {
      dataWaiter.get(1, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
      timeout = true;
    }
    assertTrue(message, timeout);
  }

  private VcsLogRefresherImpl createLoader(DataWaiter dataWaiter) {
    myDataHolder = new VcsLogDataHolder(myProject, myProject, myLogProviders,
                                                       ServiceManager.getService(myProject, VcsLogSettings.class), dataWaiter);
    return new VcsLogRefresherImpl(myProject, myDataHolder.getHashMap(), myLogProviders, myDataHolder.getUserRegistry(), myTopDetailsCache,
                                   dataWaiter, FAILING_EXCEPTION_HANDLER, RECENT_COMMITS_COUNT);
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
            return myDataHolder.getHash(integer);
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

  private static class DataWaiter extends FutureResult<DataPack> implements Consumer<DataPack> {
    private IllegalStateException myException;

    @Override
    public void consume(DataPack t) {
      try {
        set(t);
      }
      catch (IllegalStateException e) {
        myException = e;
        throw e;
      }
    }

    public boolean failed() {
      return myException != null;
    }
  }
}
