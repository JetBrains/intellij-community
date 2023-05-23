// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.NotNull
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

import static org.junit.Assert.*

@RunWith(JUnit4.class)
class DumbServiceImplTest {

  @ClassRule
  public static final ProjectRule p = new ProjectRule(true, false, null)

  @Rule
  public EdtRule edt = new EdtRule()

  private static Disposable testClassDisposable
  private static Disposable testDisposable

  private Project project

  @BeforeClass
  static void beforeAll() {
    String prevDumbQueueTasks = System.setProperty("idea.force.dumb.queue.tasks", "true")
    String prevIgnoreHeadless = System.setProperty("intellij.progress.task.ignoreHeadless", "true")

    testClassDisposable = Disposer.newDisposable("DumbServiceImplTest")
    Disposer.register(testClassDisposable) {
      SystemProperties.setProperty("idea.force.dumb.queue.tasks", prevDumbQueueTasks)
      SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", prevIgnoreHeadless)
    }
  }

  @AfterClass
  static void afterAll() throws Exception {
    Disposer.dispose(testClassDisposable)
  }

  @Before
  void setUp() throws Exception {
    testDisposable = Disposer.newDisposable("DumbServiceImplTest")
    project = p.project
    if (!ApplicationManager.application.isReadAccessAllowed()) {
      dumbService.waitForSmartMode()
    }
  }

  @After
  void tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  void "test no task leak on dispose"() {
    DumbService dumbService = new DumbServiceImpl(project)

    CountDownLatch disposes = new CountDownLatch(2)
    def task1 = new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        while (!indicator.isCanceled()) {
          Thread.sleep(50)
        }
      }

      @Override
      void dispose() {
        assert disposes.count > 0
        disposes.countDown()
      }
    }

    def task2 = new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
      }

      @Override
      void dispose() {
        assert disposes.count > 0
        disposes.countDown()
      }
    }

    // TODO: queued tasks never executed, because new DumbServiceImpl creates a service in DUMB mode
    dumbService.queueTask(task1)
    dumbService.queueTask(task2)
    Disposer.dispose(dumbService)

    assert disposes.await(5, TimeUnit.SECONDS)
    assertTrue(Disposer.isDisposed(task1))
    assertTrue(Disposer.isDisposed(task2))
  }

  @Test
  void "test queueTask is async"() {
    def semaphore = new Semaphore(1)
    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        def e = new Exception()
        for (StackTraceElement element : e.stackTrace) {
          if (element.toString().contains(DumbServiceGuiExecutor.class.simpleName)) {
            semaphore.up()
            return
          }
        }
        throw new Error("Unexpected stack trace for the DumbModeTask: ", e)
      }
    })

    assert semaphore.waitFor(1000)
  }

  @Test
  @RunsInEdt
  void "test runWhenSmart is executed synchronously in smart mode in EDT"() {
    int invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assert invocations == 1
  }

  @Test
  void "test runWhenSmart is executed on EDT without write action"() {
    int invocations = 0

    Phaser phaser = new Phaser(2)
    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //1

        EdtTestUtil.runInEdtAndWait {
          dumbService.runWhenSmart {
            ApplicationManager.application.assertIsDispatchThread()
            assert !ApplicationManager.application.writeAccessAllowed
            invocations++
            phaser.arriveAndDeregister() //2
          }
        }
      }
    })
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //1
    assert dumbService.dumb
    assert invocations == 0
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) //2
    assert invocations == 1
  }

  private DumbServiceImpl getDumbService() {
    (DumbServiceImpl)DumbService.getInstance(project)
  }

  @Test
  @RunsInEdt
  void "test no deadlocks when indexing JSP modally"() {
    def tempFixture = new TempDirTestFixtureImpl()
    Disposer.register(testDisposable) { tempFixture.tearDown() }

    // create externally and carefully refresh, avoiding eager content loading and charset detection
    def dir = new File(tempFixture.tempDirPath + '/jsps')
    dir.mkdirs()
    new File(dir, 'a.jsp').createNewFile()

    def vDir = LocalFileSystem.instance.refreshAndFindFileByIoFile(dir)
    assert vDir != null
    assert vDir.children.length == 1
    def child = vDir.children[0]
    assert child.fileType.name == 'JSP'
    assert !((VirtualFileImpl) child).charsetSet
    assert ((PsiManagerImpl)project.getService(PsiManager.class)).fileManager.getCachedPsiFile(child) == null

    def started = new AtomicBoolean()
    def finished = new AtomicBoolean()

    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        started.set(true)
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        try {
          ProgressIndicatorUtils.withTimeout(20_000) {
            def index = FileBasedIndex.getInstance() as FileBasedIndexImpl
            new IndexUpdateRunner(index, 1)
              .indexFiles(project, Collections.singletonList(new IndexUpdateRunner.FileSet(project, "child", [child])),
                          indicator, new ProjectIndexingHistoryImpl(project, "Testing", ScanningType.PARTIAL))
          }
        }
        catch (ProcessCanceledException e) {
          throw new RuntimeException("Successful indexing expected", e)
        }
        finished.set(true)
      }
    })
    assert !started.get()
    WriteAction.run { dumbService.completeJustSubmittedTasks() }
    assert started.get()
    assert finished.get()
  }

  @Test
  void testDelayBetweenBecomingSmartAndWaitForSmartReturnMustBeSmall() {
    int N = 100
    long[] delays = new long[N]
    DumbServiceImpl dumbService = getDumbService()
    Future<?> future = null
    for (int i=0; i< N; i++) {
      EdtTestUtil.runInEdtAndWait {
        dumbService.runInDumbMode {
          CountDownLatch waiting = new CountDownLatch(1)
          future = ApplicationManager.getApplication().executeOnPooledThread({
                                                                               waiting.countDown()
                                                                               dumbService.waitForSmartMode()
                                                                             } as Runnable)
          waiting.await()
        }
      }
      long start = System.currentTimeMillis()
      future.get()
      long elapsed = System.currentTimeMillis() - start
      delays[i] = elapsed
    }
    Arrays.sort(delays)
    long avg = ArrayUtil.averageAmongMedians(delays, 3)
    assert avg == 0: "Seems there's is a significant delay between becoming smart and waitForSmartMode() return. Delays in ms:\n" +
                     Arrays.toString(delays) + "\n"
  }


  @Topic.ProjectLevel
  private static final Topic<DumbService.DumbModeListener> TEST_DUMB_MODE = new Topic<>("test dumb mode",
                                                                                        DumbService.DumbModeListener.class,
                                                                                        Topic.BroadcastDirection.NONE)

  @Topic.ProjectLevel
  private static final Topic<Runnable> TEST_TOPIC = new Topic<>("test topic", Runnable.class, Topic.BroadcastDirection.NONE)

  @Test
  void "test write lock in DumbModeListener does not propagate to runWhenSmart subscribers"() {
    // The same is applicable to ScanningListener and ProjectActivity listeners in SmartModeScheduler.
    // But test only considers DumbModeListener (two other tests look exactly the same).
    // Proper solution (coming soon) - use async listeners in SmartModeScheduler (e.g. MutableStateFlow)

    // First listener.
    // Starts write action and publishes unrelated event. Publishing will propagate write action to subsequent DumbModeListener's
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      void exitDumbMode() {
        // The whole point of the test is that this write action should not propagate to DumbService#runWhenSmart
        ApplicationManager.application.runWriteAction {
          project.messageBus.syncPublisher(TEST_TOPIC).run()
        }
      }
    })

    // subscriber for event published from first listener under write action (to enforce write action propagation)
    project.messageBus.connect(testDisposable).subscribe(TEST_TOPIC) {}

    // Second listener. Verifies that write action propagates, otherwise we didn't need this test at all
    boolean writeActionPropagatesToSubscribers = false
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      void exitDumbMode() {
        writeActionPropagatesToSubscribers = ApplicationManager.application.writeAccessAllowed
      }
    })

    // Third listener. Ends dumb mode and invokes DumbService#runWhenSmart runnables. runWhenSmart runnables should not enjoy write action
    project.messageBus.connect(testDisposable).subscribe(TEST_DUMB_MODE, new SmartModeScheduler.SmartModeSchedulerDumbModeListener(project))

    // now start dumb mode
    assert project.getService(SmartModeScheduler.class).currentMode == 0
    EdtTestUtil.runInEdtAndWait {
      project.messageBus.syncPublisher(TEST_DUMB_MODE).enteredDumbMode()
    }
    assert project.getService(SmartModeScheduler.class).currentMode != 0

    // this is our runWhenSmart subscriber. We want it to not see write action started by the first listener
    CompletableFuture<Boolean> writeIntentLockInRunWhenSmart = new CompletableFuture<>()
    dumbService.runWhenSmart {
      writeIntentLockInRunWhenSmart.complete(ApplicationManager.application.writeAccessAllowed)
    }

    // Now end dumb mode and check how different listeners behave
    EdtTestUtil.runInEdtAndWait {
      project.messageBus.syncPublisher(TEST_DUMB_MODE).exitDumbMode()
    }
    dumbService.waitForSmartMode()

    assert writeActionPropagatesToSubscribers: "Assumption about platform behavior failed. " +
                                               "It is not that we want this behavior, this behavior is hard wired into the platform. " +
                                               "If we don't observe write lock started by the first listener in the second listener, " +
                                               "then the whole test does not make any sense."

    assert writeIntentLockInRunWhenSmart.get(5, TimeUnit.SECONDS) == false:
      "DumbService should not propagate write action to runWhenSmart subscribers"
  }

  @Test
  void "test cancelAllTasksAndWait cancels all the tasks submitted via queueTask from other threads with no race"() {
    AtomicBoolean queuedTaskInvoked = new AtomicBoolean(false)
    CountDownLatch dumbTaskFinished = new CountDownLatch(1)

    Thread t1 = new Thread({
                             dumbService.queueTask(
                               new DumbModeTask() {
                                 @Override
                                 void performInDumbMode(@NotNull ProgressIndicator indicator) {
                                   queuedTaskInvoked.set(true)
                                 }

                                 @Override
                                 void dispose() {
                                   dumbTaskFinished.countDown()
                                 }
                               }
                             )
                           }, "Test thread 1")

    EdtTestUtil.runInEdtAndWait {
      // we are on Write thread without write action
      t1.start()
      t1.join(5_000)
      assertFalse("Thread should have completed", t1.isAlive())

      // this should also cancel the task submitted by t1. There is no race: t1 definitely submitted this task and the thread itself finished.
      dumbService.cancelAllTasksAndWait()
    }

    dumbTaskFinished.await(5, TimeUnit.SECONDS)

    assertEquals("DumbModeTask didn't complete in 5 seconds", 0, dumbTaskFinished.count)
    assertFalse(queuedTaskInvoked.get())
  }

  @Test
  void "test dispose cancels all the tasks submitted via queueTask from other threads with no race"() {
    DumbService dumbService = new DumbServiceImpl(project)
    AtomicBoolean queuedTaskInvoked = new AtomicBoolean(false)
    CountDownLatch dumbTaskFinished = new CountDownLatch(1)

    Thread t1 = new Thread({
                             dumbService.queueTask(
                               new DumbModeTask() {
                                 @Override
                                 void performInDumbMode(@NotNull ProgressIndicator indicator) {
                                   queuedTaskInvoked.set(true)
                                 }

                                 @Override
                                 void dispose() {
                                   dumbTaskFinished.countDown()
                                 }
                               }
                             )
                           }, "Test thread 1")

    EdtTestUtil.runInEdtAndWait {
      // we are on Write thread without write action
      t1.start()
      t1.join(5_000)
      assertFalse("Thread should have completed", t1.isAlive())

      // this should also cancel the task submitted by t1. There is no race: t1 definitely submitted this task and the thread itself finished.
      Disposer.dispose(dumbService)
    }

    dumbTaskFinished.await(5, TimeUnit.SECONDS)

    assertEquals("DumbModeTask didn't dispose in 5 seconds", 0, dumbTaskFinished.count)
    assertFalse(queuedTaskInvoked.get())
  }
}
