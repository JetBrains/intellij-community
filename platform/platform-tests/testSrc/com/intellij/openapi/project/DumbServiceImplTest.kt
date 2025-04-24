// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbServiceImpl.Companion.IDEA_FORCE_DUMB_QUEUE_TASKS
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.PerProjectIndexingQueue
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner
import com.intellij.util.indexing.contentQueue.IndexingProgressReporter2
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
class DumbServiceImplTest {

  @Rule
  @JvmField
  val edt: EdtRule = EdtRule()

  private lateinit var project: Project
  private lateinit var testDisposable: CheckedDisposable

  companion object {
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)

    private lateinit var testClassDisposable: Disposable


    @BeforeClass
    @JvmStatic
    fun beforeAll() {
      val prevDumbQueueTasks = System.setProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, "true")
      val prevIgnoreHeadless = System.setProperty("intellij.progress.task.ignoreHeadless", "true")

      testClassDisposable = Disposer.newDisposable("DumbServiceImplTest")
      Disposer.register(testClassDisposable) {
        SystemProperties.setProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, prevDumbQueueTasks)
        SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", prevIgnoreHeadless)
      }
    }

    @AfterClass
    @JvmStatic
    fun afterAll() {
      Disposer.dispose(testClassDisposable)
    }
  }

  @Before
  fun setUp() {
    testDisposable = Disposer.newCheckedDisposable("DumbServiceImplTest")
    project = p.project
    if (!application.isReadAccessAllowed) {
      waitForSmartModeFiveSecondsOrThrow()
    }
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
  fun `test runWhenSmart does not hang in scheduler queue after Default dispatcher starvation`() {
    val releaseDefaultDispatcher = CountDownLatch(1)
    val inSmartMode = CountDownLatch(1)

    try {
      // occupy all the Dispatcher.Default threads with useless work
      val defaultDispatcherJob = CoroutineScope(Dispatchers.Default).launch {
        repeat(kotlinx.coroutines.scheduling.CORE_POOL_SIZE) {
          launch {
            delay(10_000)
            fail("this coroutine should have been cancelled")
          }
        }
      }

      runInEdtAndWait {
        dumbService.queue {
          dumbService.runWhenSmart {
            inSmartMode.countDown()
          }
        }

        assertTrue("Dumb mode didn't start", dumbService.isDumb)
      }

      // Wait until dumb mode is finished.
      // Don't rely on dumbService.waitForSmartMode because it may need Default dispatcher, which is still busy.
      // We want dumb service to become dumb and then smart WHILE all the dispatcher threads are busy, so all the StateFlows listeners
      //   missed both these events (due to conflation)
      for (i in 1..10) {
        if (!dumbService.isDumb) break
        Thread.sleep(100)
      }
      assertFalse("Dumb mode didn't finish", runInEdtAndGet { dumbService.isDumb })

      // now release Default dispatcher and see if runnable is executed (it should)
      defaultDispatcherJob.cancel()
      inSmartMode.awaitOrThrow(5, "Smart mode runnable didn't run")
    }
    finally {
      releaseDefaultDispatcher.countDown()
    }
  }

  private fun DumbService.queue(task: (ProgressIndicator) -> Unit) {
    queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) = task(indicator)
    })
  }

  @Test
  fun `test no task leak on dispose`() = runBlocking {
    // pass empty publisher to make sure that shared SmartModeScheduler is not affected
    val dumbService = DumbServiceImpl(project, object : DumbService.DumbModeListener {}, this)
    val exception = AtomicReference<Throwable?>()

    val disposes = CountDownLatch(2)
    val task1 = object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        try {
          while (!indicator.isCanceled) {
            Thread.sleep(50)
            assertFalse(testDisposable.isDisposed)
          }
        }
        catch (t: Throwable) {
          exception.set(t)
        }
      }

      override fun dispose() {
        try {
          assertTrue(disposes.count > 0)
          disposes.countDown()
        }
        catch (t: Throwable) {
          exception.set(t)
        }
      }
    }

    val task2 = object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) = Unit

      override fun dispose() {
        try {
          assertTrue(disposes.count > 0)
          disposes.countDown()
        }
        catch (t: Throwable) {
          exception.set(t)
        }
      }
    }

    dumbService.queueTask(task1)
    dumbService.queueTask(task2)
    runInEdtAndWait { Disposer.dispose(dumbService) }

    disposes.awaitOrThrow(5, "Some tasks were not disposed after 5 seconds")
    assertTrue(Disposer.isDisposed(task1))
    assertTrue(Disposer.isDisposed(task2))
    assertNull(exception.get())
  }

  @Test
  fun `test queueTask is async`() {
    val semaphore = Semaphore(1)
    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        val e = Exception()
        for (element in e.stackTrace) {
          if (element.toString().contains(DumbServiceGuiExecutor::class.java.simpleName)) {
            semaphore.up()
            return
          }
        }
        throw Error("Unexpected stack trace for the DumbModeTask: ", e)
      }
    })

    assertTrue(semaphore.waitFor(1000))
  }

  @Test
  @RunsInEdt
  fun `test runWhenSmart is executed synchronously in smart mode in EDT`()
  {
    var invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assertEquals(1, invocations)
  }

  @Test
  fun `test runWhenSmart is executed on EDT without write action`()
  {
    var invocations = 0

    val phaser = Phaser(2)
    dumbService.queueTask(object :DumbModeTask () {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //1
        phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //2

        runInEdtAndWait {
          dumbService.runWhenSmart {
            ThreadingAssertions.assertEventDispatchThread()
            assertFalse(application.isWriteAccessAllowed)
            invocations++
            phaser.arriveAndDeregister() //3
          }
        }
      }
    })
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //1
    assertTrue(dumbService.isDumb)
    assertEquals(0,invocations)
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //2
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //3
    assertEquals(1, invocations)
    phaser.arriveAndDeregister()
  }

  private val dumbService by lazy { DumbService.getInstance(project) }

  @Test
  @RunsInEdt
  fun `test no deadlocks when indexing JSP modally`() {
    val tempFixture = TempDirTestFixtureImpl()
    Disposer.register(testDisposable) { tempFixture.tearDown() }

    // create externally and carefully refresh, avoiding eager content loading and charset detection
    val dir = File(tempFixture.tempDirPath + "/jsps")
    dir.mkdirs()
    File(dir, "a.jsp").createNewFile()

    val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)!!
    assertEquals(1, vDir.children.size)
    val child = vDir.children[0]
    assertEquals("JSP", child.fileType.name)
    assertFalse((child as VirtualFileImpl).isCharsetSet)
    assertNull(PsiManagerEx.getInstanceEx(project).fileManager.getCachedPsiFile(child))

    val started = AtomicBoolean()
    val finished = AtomicBoolean()

    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        started.set(true)
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        try {
          ProgressIndicatorUtils.withTimeout(20_000) {
            val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
            IndexUpdateRunner(index, project.service<ProjectIndexingDependenciesService>().getLatestIndexingRequestToken())
              .indexFiles(project, IndexUpdateRunner.FileSet(project, "child",
                                                             PerProjectIndexingQueue.QueuedFiles.fromFilesCollection(listOf(child), emptyList())),
                          ProjectDumbIndexingHistoryImpl(project),
                          IndexingProgressReporter2.createEmpty())
          }
        }
        catch (e: ProcessCanceledException) {
          throw RuntimeException("Successful indexing expected", e)
        }
        finished.set(true)
      }
    })
    assertFalse(started.get())
    WriteAction.run<Throwable> { dumbService.completeJustSubmittedTasks() }
    assertTrue(started.get())
    assertTrue(finished.get())
  }

  @Test
  fun testDelayBetweenBecomingSmartAndWaitForSmartReturnMustBeSmall() {
    val N = 100
    val delays = LongArray(N)
    val dumbService = dumbService
    lateinit var future: Future<*>
    for (i in 0 until N) {
      runInEdtAndWait {
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
          val waiting = CountDownLatch(1)
          future = application.executeOnPooledThread {
            waiting.countDown()
            dumbService.waitForSmartMode()
          }
          waiting.awaitOrThrow(1, "Should have completed quickly")
        }
      }
      val start = System.currentTimeMillis()
      future.get()
      val elapsed = System.currentTimeMillis() - start
      delays[i] = elapsed
    }
    delays.sort()
    val avg = ArrayUtil.averageAmongMedians(delays, 3)
    assertEquals("Seems there's is a significant delay between becoming smart and waitForSmartMode() return. Delays in ms:\n" +
                 delays + "\n",
                 0, avg)
  }

  @Test
  fun `test cancelAllTasksAndWait cancels all the tasks submitted via queueTask from other threads with no race`() {
    val queuedTaskInvoked = AtomicBoolean(false)
    val dumbTaskFinished = CountDownLatch(1)

    val t1 = Thread {
      dumbService.queueTask(
        object : DumbModeTask() {
          override fun performInDumbMode(indicator: ProgressIndicator) {
            queuedTaskInvoked.set(true)
          }

          override fun dispose() {
            dumbTaskFinished.countDown()
          }
        }
      )
    }

    runInEdtAndWait {
      // we are on Write thread without write action
      t1.start()
      t1.join(5_000)
      assertFalse("Thread should have completed", t1.isAlive)

      // this should also cancel the task submitted by t1. There is no race: t1 definitely submitted this task and the thread itself finished.
      dumbService.cancelAllTasksAndWait()
    }

    dumbTaskFinished.awaitOrThrow(5, "DumbModeTask didn't complete in 5 seconds")
    assertFalse(queuedTaskInvoked.get())
  }

  @Test
  fun `test dispose cancels all the tasks submitted via queueTask from other threads with no race`() = runBlocking {
    val serviceScope = childScope("DumbServiceImpl")
    // pass empty publisher to make sure that shared SmartModeScheduler is not affected
    val dumbService = DumbServiceImpl(project, object : DumbService.DumbModeListener {}, serviceScope)

    val queuedTaskInvoked = AtomicBoolean(false)
    val dumbTaskFinished = CountDownLatch(1)

    val t1 = Thread {
      dumbService.queueTask(
        object : DumbModeTask() {
          override fun performInDumbMode(indicator: ProgressIndicator) {
            queuedTaskInvoked.set(true)
          }

          override fun dispose() {
            dumbTaskFinished.countDown()
          }
        }
      )
    }

    runInEdtAndWait {
      // we are on Write thread without write action
      t1.start()
      t1.join(5_000)
      assertFalse("Thread should have completed", t1.isAlive())

      // this should also cancel the task submitted by t1. There is no race: t1 definitely submitted this task and the thread itself finished.
      Disposer.dispose(dumbService)
    }

    dumbTaskFinished.awaitOrThrow(5, "DumbModeTask didn't dispose in 5 seconds")

    assertFalse(queuedTaskInvoked.get())
    serviceScope.cancel()
  }

  @Test
  fun `test DumbService becomes dumb immediately on EDT`() {
    runInEdtAndWait {
      assertFalse(dumbService.isDumb)
      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) = Unit
      })
      assertTrue(dumbService.isDumb)
    }
  }

  @Test
  fun `test DumbService does not become dumb in the middle of read action`() {
    val taskFinished = CountDownLatch(1)

    application.runReadAction {
      assertFalse(dumbService.isDumb)

      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) = taskFinished.countDown()
      })

      // Wait a bit for dumb task to start (it is expected that it does not start until the end of read action)
      assertFalse(taskFinished.await(200, TimeUnit.MILLISECONDS))
      assertFalse("Read action should prevent DumbService from entering into dumb mode", dumbService.isDumb)
    }

    taskFinished.awaitOrThrow(2, "DumbTask should start immediately after read action finished")
  }

  @Test
  fun `test DumbService does not become smart in the middle of read action`() {
    val finishTask = CountDownLatch(1)
    val taskFinished = CountDownLatch(1)
    val taskStarted = CountDownLatch(1)

    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        taskStarted.countDown()
        finishTask.awaitOrThrow(5, "Task should have finished")
        taskFinished.countDown()
      }
    })

    assertTrue(taskStarted.await(5, TimeUnit.MILLISECONDS))
    application.runReadAction {
      assertTrue(dumbService.isDumb)
      finishTask.countDown()
      assertTrue(taskFinished.await(5, TimeUnit.MILLISECONDS))
      // Wait a bit for dumb mode to end (it is expected that it does not end until the end of read action)
      Thread.sleep(200)
      assertTrue(dumbService.isDumb)
    }

    waitForSmartModeFiveSecondsOrThrow()
  }

  @Test
  fun `test DumbService does not execute tasks immediately to give a chance to completeJustSubmittedTasks`() {
    val taskFinished = CountDownLatch(1)
    val startEdtTask = CountDownLatch(1)
    val exception = AtomicReference<Throwable?>()

    runInEdt /*Async*/ {
      try {
        // occupy EDT to postpone dumb task execution
        startEdtTask.awaitOrThrow(5, "Task should have start in 5 seconds")
        assertFalse("DumbTask should not start until EDT is freed", taskFinished.await(5, TimeUnit.MILLISECONDS))
      }
      catch (t: Throwable) {
        exception.set(t)
      }
    }

    // queue task while EDT is busy
    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) = taskFinished.countDown()
    })

    startEdtTask.countDown()
    taskFinished.awaitOrThrow(2, "DumbTask should start immediately after EDT is freed")
    assertNull(exception.get())
  }

  @Test
  fun `test dumb mode continues after new tasks submitted on EDT`() {
    // this test checks the following situation: dumb queue executor finished, but cannot invoke `updateFinished` because EDT is busy
    // meanwhile new tasks placed by the EDT thread, and this should continue dumb mode until after new tasks are completed.
    val dumbTaskStarted1 = CountDownLatch(1)
    val dumbTaskStarted2 = CountDownLatch(1)
    val finishDumbTask1 = CountDownLatch(1)
    val finishDumbTask2 = CountDownLatch(1)
    val exception = AtomicReference<Throwable?>()

    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        try {
          dumbTaskStarted1.countDown()
          finishDumbTask1.awaitOrThrow(5, "No command to finish dumb task1 after 5 seconds")
        }
        catch (t: Throwable) {
          exception.set(t)
        }
      }
    })

    dumbTaskStarted1.awaitOrThrow(5, "Dumb task1 didn't start in 5 seconds")
    runInEdtAndWait {
      assertTrue("Dumb service should be dumb because first task has not completed yet", dumbService.isDumb)
      finishDumbTask1.countDown() // finish task while EDT is busy

      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          try {
            dumbTaskStarted2.countDown()
            finishDumbTask2.awaitOrThrow(5, "No command to finish dumb task2 after 5 seconds")
          }
          catch (t: Throwable) {
            exception.set(t)
          }
        }
      })

      repeat(5) {
        UIUtil.dispatchAllInvocationEvents() // pump events and make sure that dumb mode hasn't ended
        assertTrue("Dumb service should be dumb because the second task is already queued and it was queued on EDT", dumbService.isDumb)
        Thread.sleep(50)
      }
    }

    dumbTaskStarted2.awaitOrThrow(5, "Dumb task2 didn't start in 5 seconds")
    assertTrue("Dumb service should be dumb because second task has not completed yet", dumbService.isDumb)
    finishDumbTask2.countDown()

    waitForSmartModeFiveSecondsOrThrow()

    assertNull(exception.get())
  }

  @Test
  fun `test startEternalDumbModeTask and endEternalDumbModeTaskAndWaitForSmartMode do not hang when invoked from EDT`() {
    runInEdtAndWait {
      val dumbTask = DumbModeTestUtils.startEternalDumbModeTask(project)
      DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, dumbTask)
    }
  }


  @Test
  fun `test startEternalDumbModeTask and endEternalDumbModeTaskAndWaitForSmartMode from background thread`() {
    val dumbTask = DumbModeTestUtils.startEternalDumbModeTask(project)
    DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, dumbTask)
  }

  @Test
  fun `test startEternalDumbModeTask and endEternalDumbModeTaskAndWaitForSmartMode do not hang when invoked from EDT modally`() {
    runBlocking(Dispatchers.EDT) {
      withModalProgress(project, "test") {
        val dumbTask = DumbModeTestUtils.startEternalDumbModeTask(project)
        DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, dumbTask)
      }
    }
  }

  private fun <E> permutationsOf(original: List<E> = listOf(), resultsCollector: List<E> = mutableListOf()): List<List<E>> =
    if (original.isEmpty()) listOf(resultsCollector)
    else original.flatMap { permutationsOf((original - it), resultsCollector + it) }

  @Test
  fun `two concurrent dumb tasks start and finish in all possible orders`() = runBlocking {
    val taskIds = listOf(0, 1)
    val startFinishCombinations = mutableListOf<Pair<List<Int>, List<Int>>>()
    permutationsOf(taskIds).forEach { startSeq ->
      permutationsOf(taskIds).forEach { endSeq ->
        startFinishCombinations.add(Pair(startSeq, endSeq))
      }
    }

    startFinishCombinations.forEachIndexed { i, (startSeq, finishSeq) ->
      assertFalse("$i: Should be smart before the first submitted task. startOrder: $startSeq, finishOrder: $finishSeq",
                  dumbService.isDumb)

      val triggers = Array(taskIds.size) { CompletableDeferred<Boolean>() }
      val started = Array(taskIds.size) { CompletableDeferred<Boolean>() }
      val tasks = Array(taskIds.size) { idx ->
        launch(start = CoroutineStart.LAZY) {
          dumbService.runInDumbMode {
            assertTrue("$i,$idx: Should be dumb inside runInDumbMode. startOrder: $startSeq, finishOrder: $finishSeq", dumbService.isDumb)
            started[idx].complete(true)
            triggers[idx].await()
          }
        }
      }

      // Start the tasks in order
      startSeq.forEach { idx ->
        tasks[idx].start()
        started[idx].await()
        assertTrue("$i,$idx: Should be dumb after the first submitted task. startOrder: $startSeq, finishOrder: $finishSeq",
                   dumbService.isDumb)
      }

      // All the tasks are started. Finish them in order
      finishSeq.forEach { idx ->
        assertTrue("$i,$idx: Should be dumb before finishing the last task. startOrder: $startSeq, finishOrder: $finishSeq",
                   dumbService.isDumb)
        triggers[idx].complete(true)
        tasks[idx].join()
      }

      assertFalse("$i: Should be smart after finishing last task. startOrder: $startSeq, finishOrder: $finishSeq", dumbService.isDumb)
    }
  }

  private val asyncDumbStartModes = listOf<suspend CoroutineScope.(suspend () -> Unit) -> Unit>(
    { task ->
      dumbService.queue {
        runBlockingCancellable {
          task()
        }
      }
    },
    { task ->
      launch {
        dumbService.runInDumbMode {
          task()
        }
      }
    }
  )

  @Test
  fun `dumb mode does not start from background thread if modal dialog shown`() {
    asyncDumbStartModes.forEachIndexed { idx, startDumbTask ->
      val startModal = CompletableDeferred<Boolean>()
      val endModal = CompletableDeferred<Boolean>()
      val startDumb = CompletableDeferred<Boolean>()
      val endDumb = CompletableDeferred<Boolean>()
      try {
        assertFalse("Mode $idx: Should not be dumb in the beginning of the test", dumbService.isDumb)
        runBlocking {
          withTimeout(5_000) {
            launch(Dispatchers.EDT) {
              withModalProgress(project, "test") {
                startModal.complete(true)
                endModal.await()
              }
            }

            launch(Dispatchers.IO) {
              startModal.await()
              startDumbTask {
                startDumb.complete(true)
                endDumb.await()
              }

              repeat(10) {
                assertFalse("Mode $idx: Should not enter dumb mode from background thread if modal task is running", dumbService.isDumb)
                delay(100)
              }
              endModal.complete(true)

              startDumb.await() // wait for dumb mode to start after modal dialog finished
              endDumb.complete(true)
            }
          }

          waitForSmartModeFiveSecondsOrThrow()
        }
      }
      catch (t: Throwable) {
        throw AssertionError("Test failed in mode $idx: ${t.message}", t)
      }
      finally {
        startModal.complete(true)
        endModal.complete(true)
        startDumb.complete(true)
        endDumb.complete(true)
      }
    }
  }

  @Test
  fun `dumb mode can start and end from modal context (async)`() {
    asyncDumbStartModes.forEachIndexed { idx, startDumbTask ->
      val startDumb = CompletableDeferred<Boolean>()
      val endDumb = CompletableDeferred<Boolean>()
      try {
        runBlocking(Dispatchers.EDT) {
          withTimeout(5_000) {
            withModalProgress(project, "test") {
              startDumbTask {
                startDumb.complete(true)
                endDumb.await()
              }

              startDumb.await()
              assertTrue("Mode $idx: Should be able to start dumb mode from modal task", dumbService.isDumb)

              endDumb.complete(true)

              withContext(Dispatchers.IO) {
                // should be able to finish dumb mode in modal context, because it was started in modal context
                waitForSmartModeFiveSecondsOrThrow()
              }
            }
          }
        }
      }
      catch (t: Throwable) {
        throw AssertionError("Test failed in mode $idx: ${t.message}", t)
      }
      finally {
        startDumb.complete(true)
        endDumb.complete(true)
      }
    }
  }

  @Test
  fun `dumb mode can be started and ended from modal context (sync)`() {
    runBlocking(Dispatchers.EDT) {
      withTimeout(5_000) {
        withModalProgress(project, "test") {
          val executed = dumbService.runInDumbMode {
            assertTrue("Should be able to start dumb mode from modal task", dumbService.isDumb)
            true
          }

          assertTrue("runInDumbMode should have finished", executed)
          assertFalse("Should be able to end dumb mode from modal task", dumbService.isDumb)
        }
      }
    }
  }

  @Test
  fun `DumbService_queue works from suspend context`() {
    val dispatchersToTry = listOf(Dispatchers.EDT, Dispatchers.Default, Dispatchers.IO)

    dispatchersToTry.forEach { dispatcher ->
      assertFalse("$dispatcher: Should be in smart mode before test", DumbService.isDumb(project))
      runBlocking(dispatcher) {
        withTimeout(5_000) {
          val taskStarted = CompletableDeferred<Boolean>()
          val taskFinished = CountDownLatch(1)
          dumbService.queue {
            taskStarted.complete(true)
            taskFinished.awaitOrThrow(5, "Test didn't finish dumb mode. Finish it on timeout.")
          }
          if (dispatcher == Dispatchers.EDT) {
            assertTrue("Should become dumb immediately, when on EDT", DumbService.isDumb(project))
          }
          taskStarted.await()
          assertTrue("$dispatcher: Should be dumb when running dumb task", DumbService.isDumb(project))
          taskFinished.countDown()

          DumbModeTestUtils.waitForSmartMode(project)
        }
      }
    }
  }

  @Test
  fun `DumbService_queue works when invoked from undispatched EDT via blockingContext`() {
    runInEdtAndWait {
      CoroutineScope(Job()).launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
        blockingContext {
          val taskStarted = CountDownLatch(1)
          val taskFinished = CountDownLatch(1)
          dumbService.queue {
            taskStarted.countDown()
            taskFinished.awaitOrThrow(5, "Test didn't finish dumb mode. Finish it on timeout.")
          }
          assertTrue("Should become dumb immediately, when on EDT", DumbService.isDumb(project))
          PlatformTestUtil.waitWithEventsDispatching("Dumb task didn't start after 5 seconds.", { taskStarted.count == 0L }, 5)
          assertTrue("Should be dumb when running dumb task", DumbService.isDumb(project))
          taskFinished.countDown()

          DumbModeTestUtils.waitForSmartMode(project)
        }
      }.invokeOnCompletion { t ->
        if (t != null) {
          throw AssertionError(t)
        }
      }
    }
  }

  @Test
  fun `DumbService_runInDumbMode should properly handle coroutine cancellation`() = runBlocking(Dispatchers.EDT) {
    val dumbTaskStarted = Job()
    val dumbTask = launch(Dispatchers.Default) {
      dumbService.runInDumbMode("Test dumb task that awaits cancellation") {
        dumbTaskStarted.complete()
        awaitCancellation()
      }
    }
    withTimeout(10.seconds) {
      dumbTaskStarted.join()

      dumbService.state.first { it.isDumb }
      dumbTask.cancel("Cancel dumb task")
      dumbService.state.first { !it.isDumb }
    }
    return@runBlocking
  }

  private fun waitForSmartModeFiveSecondsOrThrow() {
    if (!dumbService.waitForSmartMode(5_000)) {
      dumbService.waitForSmartMode(5_000)
      fail("Could not reach smart mode after 5 seconds")
    }
  }

  private fun CountDownLatch.awaitOrThrow(seconds: Long, message: String) {
    if (!await(seconds, TimeUnit.SECONDS)) {
      fail(message)
    }
  }
}
