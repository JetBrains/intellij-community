// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbServiceImpl.Companion.IDEA_FORCE_DUMB_QUEUE_TASKS
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
  fun `test no task leak on dispose`() {
    // pass empty publisher to make sure that shared SmartModeScheduler is not affected
    val dumbService = DumbServiceImpl(project, object : DumbService.DumbModeListener {})
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

    runInEdtAndWait { dumbService.isDumb = false }
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
            application.assertIsDispatchThread()
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

  private val dumbService by lazy { DumbServiceImpl.getInstance(project) }

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
    assertNull((project.service<PsiManager>() as PsiManagerImpl).fileManager.getCachedPsiFile(child))

    val started = AtomicBoolean()
    val finished = AtomicBoolean()

    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        started.set(true)
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        try {
          ProgressIndicatorUtils.withTimeout(20_000) {
            val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
            IndexUpdateRunner(index, 1)
              .indexFiles(project, listOf(IndexUpdateRunner.FileSet(project, "child", listOf(child))),
                          indicator, ProjectIndexingHistoryImpl(project, "Testing", ScanningType.PARTIAL),
                          ProjectDumbIndexingHistoryImpl(project))
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
        dumbService.runInDumbMode {
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
  fun `test write lock in DumbModeListener does not propagate to runWhenSmart subscribers`() {
    // The same is applicable to ScanningListener and ProjectActivity listeners in SmartModeScheduler.
    // But test only considers DumbModeListener (two other tests look exactly the same).
    // Proper solution (coming soon) - use async listeners in SmartModeScheduler (e.g. MutableStateFlow)

    val TEST_DUMB_MODE: Topic<DumbService.DumbModeListener> = Topic("test dumb mode",
                                                                    DumbService.DumbModeListener::class.java,
                                                                    Topic.BroadcastDirection.NONE)

    val TEST_TOPIC: Topic<Runnable> = Topic("test topic", Runnable::class.java, Topic.BroadcastDirection.NONE)

    // First listener.
    // Starts write action and publishes unrelated event. Publishing will propagate write action to subsequent DumbModeListener's
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // The whole point of the test is that this write action should not propagate to DumbService#runWhenSmart
        application.runWriteAction {
          project.messageBus.syncPublisher(TEST_TOPIC).run()
        }
      }
    })

    // subscriber for event published from first listener under write action (to enforce write action propagation)
    project.messageBus.connect(testDisposable).subscribe(TEST_TOPIC, Runnable { })

    // Second listener. Verifies that write action propagates, otherwise we didn't need this test at all
    var writeActionPropagatesToSubscribers = false
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        writeActionPropagatesToSubscribers = application.isWriteAccessAllowed
      }
    })

    // Third listener. Ends dumb mode and invokes DumbService#runWhenSmart runnables. runWhenSmart runnables should not enjoy write action
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, SmartModeScheduler.SmartModeSchedulerDumbModeListener(project))

    // now start dumb mode
    assertEquals(0, project.service<SmartModeScheduler>().getCurrentMode())
    runInEdtAndWait {
      project.messageBus.syncPublisher(TEST_DUMB_MODE).enteredDumbMode()
    }
    assertNotEquals(0, project.service<SmartModeScheduler>().getCurrentMode())

    // this is our runWhenSmart subscriber. We want it to not see write action started by the first listener
    val writeIntentLockInRunWhenSmart = CompletableFuture<Boolean>()
    dumbService.runWhenSmart {
      writeIntentLockInRunWhenSmart.complete(application.isWriteAccessAllowed)
    }

    // Now end dumb mode and check how different listeners behave
    runInEdtAndWait {
      project.messageBus.syncPublisher(TEST_DUMB_MODE).exitDumbMode()
    }
    dumbService.waitForSmartMode()

    assertTrue("Assumption about platform behavior failed. " +
               "It is not that we want this behavior, this behavior is hard wired into the platform. " +
               "If we don't observe write lock started by the first listener in the second listener, " +
               "then the whole test does not make any sense.",
               writeActionPropagatesToSubscribers)

    assertFalse("DumbService should not propagate write action to runWhenSmart subscribers",
                writeIntentLockInRunWhenSmart.get(5, TimeUnit.SECONDS))
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
  fun `test dispose cancels all the tasks submitted via queueTask from other threads with no race`() {
    // pass empty publisher to make sure that shared SmartModeScheduler is not affected
    val dumbService = DumbServiceImpl(project, object : DumbService.DumbModeListener {})

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

    assertTrue(taskStarted.await(500, TimeUnit.MILLISECONDS))
    application.runReadAction {
      assertTrue(dumbService.isDumb)
      finishTask.countDown()
      assertTrue(taskFinished.await(500, TimeUnit.MILLISECONDS))
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
        assertFalse("DumbTask should not start until EDT is freed", taskFinished.await(500, TimeUnit.MILLISECONDS))
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
