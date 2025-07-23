// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ExceptionUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedCommitParser
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.test.VcsPlatformTest
import junit.framework.TestCase
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.util.*
import java.util.concurrent.*
import java.util.function.Consumer
import kotlin.concurrent.Volatile

private const val RECENT_COMMITS_COUNT = 2

class VcsLogRefresherTest : VcsPlatformTest() {
  private lateinit var logProvider: TestVcsLogProvider
  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  private lateinit var refresherTestHelper: LogRefresherTestHelper
  private lateinit var dataWaiter: DataWaiter
  private lateinit var loader: VcsLogRefresher

  private lateinit var commits: List<String>

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()

    logProvider = TestVcsLogProvider()
    val logProviders = mapOf(projectRoot to logProvider)

    commits = listOf("3|-a2|-a1", "2|-a1|-a", "1|-a|-")
    logProvider.appendHistory(TimedCommitParser.log(commits))
    logProvider.addRef(createBranchRef("master", "a2"))
    @Suppress("RAW_SCOPE_CREATION")
    testCs = CoroutineScope(SupervisorJob())
    logData = VcsLogData(project, testCs, logProviders, LoggingErrorHandler(LOG), VcsLogSharedSettings.isIndexSwitchedOn(project))
    refresherTestHelper = LogRefresherTestHelper(testCs.childScope("Refresher"), logData, RECENT_COMMITS_COUNT)

    dataWaiter = refresherTestHelper.dataWaiter
    loader = refresherTestHelper.loader
  }

  public override fun tearDown() {
    try {
      refresherTestHelper.tearDown()
      runBlocking {
        testCs.coroutineContext.job.cancelAndJoin()
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories(): Collection<String> {
    return listOf("#" + SingleTaskController::class.java.getName(), "#" + VcsLogRefresherImpl::class.java.getName(),
                  "#" + VcsLogRefresherTest::class.java.getName(), "#" + TestVcsLogProvider::class.java.getName())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun test_initialize_shows_short_history() {
    logProvider.blockFullLog()
    loader.initialize()
    val result = dataWaiter.get()
    logProvider.unblockFullLog()
    assertNotNull(result)
    assertDataPack(TimedCommitParser.log(commits.subList(0, RECENT_COMMITS_COUNT)), result.permanentGraph.allCommits)
    refresherTestHelper.waitForBackgroundTasksToComplete()
    dataWaiter.get()
  }

  @Throws(InterruptedException::class)
  fun test_first_refresh_reports_full_history() {
    loader.initialize()

    val firstDataPack = dataWaiter.get()
    assertDataPack(TimedCommitParser.log(commits.subList(0, RECENT_COMMITS_COUNT)), firstDataPack.permanentGraph.allCommits)

    val fullDataPack = dataWaiter.get()
    assertDataPack(TimedCommitParser.log(commits), fullDataPack.permanentGraph.allCommits)
  }

  @Throws(InterruptedException::class)
  fun test_first_refresh_waits_for_full_log() {
    logProvider.blockFullLog()
    loader.initialize()
    dataWaiter.get()
    refresherTestHelper.assertTimeout("Refresh waiter should have failed on the timeout")
    logProvider.unblockFullLog()

    val result = dataWaiter.get()
    assertDataPack(TimedCommitParser.log(commits), result.permanentGraph.allCommits)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun test_refresh_captures_new_commits() {
    refresherTestHelper.initAndWaitForFirstRefresh()

    val newCommit = "4|-a3|-a2"
    logProvider.appendHistory(TimedCommitParser.log(newCommit))
    loader.refresh(listOf(projectRoot), false)
    val result = dataWaiter.get()

    val allCommits: MutableList<String?> = ArrayList<String?>()
    allCommits.add(newCommit)
    allCommits.addAll(commits)
    assertDataPack(TimedCommitParser.log(allCommits), result.permanentGraph.allCommits)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun test_single_refresh_causes_single_data_read() {
    refresherTestHelper.initAndWaitForFirstRefresh()

    logProvider.resetReadFirstBlockCounter()
    loader.refresh(listOf(projectRoot), false)
    dataWaiter.get()
    TestCase.assertEquals("Unexpected first block read count", 1, logProvider.getReadFirstBlockCounter())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun test_reinitialize_makes_refresh_cancelled() {
    refresherTestHelper.initAndWaitForFirstRefresh()

    // initiate the refresh and make it hang
    logProvider.blockRefresh()
    loader.refresh(listOf(projectRoot), false)

    // initiate reinitialize; the full log will await because the Task is busy waiting for the refresh
    loader.initialize()

    // the task queue now contains (1) blocked ongoing refresh request; (2) queued complete refresh request
    // we want to make sure only one data pack is reported
    logProvider.unblockRefresh()
    dataWaiter.get()
    refresherTestHelper.assertNoMoreResultsArrive()
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun test_two_immediately_consecutive_refreshes_causes_only_one_data_pack_update() {
    refresherTestHelper.initAndWaitForFirstRefresh()
    logProvider.blockRefresh()
    loader.refresh(listOf(projectRoot), false) // this refresh hangs in VcsLogProvider.readFirstBlock()
    loader.refresh(listOf(projectRoot), false) // this refresh is queued
    logProvider.unblockRefresh() // this will make the first one complete, and then perform the second as well

    dataWaiter.get()
    refresherTestHelper.assertTimeout("Second refresh shouldn't cause the data pack update") // it may also fail in beforehand in set().
  }

  private class LogRefresherTestHelper(cs: CoroutineScope, logData: VcsLogData, recentCommitsCount: Int) {
    private val project = logData.project

    val dataWaiter = DataWaiter()
    val loader = VcsLogRefresherImpl(cs, logData.storage, logData.logProviders, VcsLogProgress(project),
                                              null, dataWaiter, recentCommitsCount
    ).apply {
      refreshBatchJobConsumer = {
        startedTasks.add(it.asCompletableFuture())
        LOG.debug(startedTasks.size.toString() + " started tasks")
      }
    }

    private val startedTasks = Collections.synchronizedList(ArrayList<Future<*>>())

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun initAndWaitForFirstRefresh() {
      // wait for the first block and the whole log to complete
      loader.initialize()

      val firstDataPack = dataWaiter.get()
      assertFalse(firstDataPack.isFull)

      val fullDataPack = dataWaiter.get()
      assertTrue(fullDataPack.isFull)
      assertNoMoreResultsArrive()
    }

    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    fun tearDown() {
      assertNoMoreResultsArrive()
      dataWaiter.tearDown()
      if (dataWaiter.failed()) {
        fail("Only one refresh should have happened, an error happened instead: " + dataWaiter.exceptionText)
      }
    }

    @Throws(InterruptedException::class)
    fun assertTimeout(message: String) {
      assertNull(message, dataWaiter.queue.poll(500, TimeUnit.MILLISECONDS))
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun assertNoMoreResultsArrive() {
      waitForBackgroundTasksToComplete()
      assertTrue(dataWaiter.queue.isEmpty())
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun waitForBackgroundTasksToComplete() {
      for (task in ArrayList(startedTasks)) {
        task.get(1, TimeUnit.SECONDS)
      }
    }

  }

  private fun assertDataPack(expectedLog: List<TimedVcsCommit>, actualLog: List<GraphCommit<Int>>) {
    fun getHash(commitIndex: Int): Hash = logData.getCommitId(commitIndex)!!.hash

    val convertedActualLog = actualLog.map { commit ->
      TimedVcsCommitImpl(getHash(commit.getId()), commit.getParents().map(::getHash), commit.getTimestamp())
    }
    assertOrderedEquals(convertedActualLog, expectedLog)
  }

  private fun createBranchRef(name: String, commit: String): VcsRefImpl {
    return VcsRefImpl(HashImpl.build(commit), name, TestVcsLogProvider.BRANCH_TYPE, projectRoot)
  }

  private class DataWaiter : Consumer<DataPack> {
    @Volatile
    private var _queue: BlockingQueue<DataPack>? = ArrayBlockingQueue(10)
    val queue: BlockingQueue<DataPack>
      get() = _queue!!

    @Volatile
    private var exception: Exception? = null

    override fun accept(t: DataPack) {
      try {
        queue.add(t)
      }
      catch (e: Exception) {
        exception = e
        throw AssertionError(e)
      }
    }

    @JvmOverloads
    @Throws(InterruptedException::class)
    fun get(timeout: Long = 1, timeUnit: TimeUnit = TimeUnit.SECONDS): DataPack {
      return queue.poll(timeout, timeUnit)!!
    }

    fun failed(): Boolean {
      return exception != null
    }

    val exceptionText: String
      get() = ExceptionUtil.getThrowableText(exception!!)

    fun tearDown() {
      _queue = null
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogRefresherTest::class.java)
  }
}
