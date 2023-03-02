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
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import static org.junit.Assert.*

@RunWith(JUnit4.class)
@RunsInEdt
class DumbServiceImplTest {

  @ClassRule
  public static final ProjectRule p = new ProjectRule(true, false, null)

  @Rule
  public EdtRule edt = new EdtRule()

  private static Disposable disposable

  private Project project

  @BeforeClass
  static void beforeAll() {
    String prevDumbQueueTasks = System.setProperty("idea.force.dumb.queue.tasks", "true")
    String prevIgnoreHeadless = System.setProperty("intellij.progress.task.ignoreHeadless", "true")

    disposable = Disposer.newDisposable("DumbServiceImplTest")
    Disposer.register(disposable) {
      SystemProperties.setProperty("idea.force.dumb.queue.tasks", prevDumbQueueTasks)
      SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", prevIgnoreHeadless)
    }
  }

  @AfterClass
  static void afterAll() throws Exception {
    Disposer.dispose(disposable)
  }

  @Before
  void setUp() throws Exception {
    project = p.project
  }

  @Test
  void "test no task leak on dispose"() {
    DumbService dumbService = new DumbServiceImpl(project)

    AtomicInteger disposes = new AtomicInteger(0)
    def task1 = new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        while (!indicator.isCanceled()) {
          Thread.sleep(50)
        }
      }

      @Override
      void dispose() {
        disposes.incrementAndGet()
      }
    }

    def task2 = new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
      }

      @Override
      void dispose() {
        disposes.incrementAndGet()
      }
    }

    dumbService.queueTask(task1)
    dumbService.queueTask(task2)
    Disposer.dispose(dumbService)

    assertEquals(2, disposes.get())
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

    UIUtil.dispatchAllInvocationEvents()
    assert semaphore.waitFor(1000)
    UIUtil.dispatchAllInvocationEvents()
  }

  @Test
  void "test runWhenSmart is executed synchronously in smart mode"() {
    int invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assert invocations == 1
  }

  @Test
  void "test runWhenSmart is executed on EDT without write action"() {
    ApplicationManager.application.assertIsDispatchThread()
    int invocations = 0

    CountDownLatch latch = new CountDownLatch(2)
    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()

        EdtTestUtil.runInEdtAndWait {
          dumbService.runWhenSmart {
            invocations++
            ApplicationManager.application.assertIsDispatchThread()
            assert !ApplicationManager.application.writeAccessAllowed
            latch.countDown()
          }
        }
        TimeoutUtil.sleep(100)
        latch.countDown()
      }
    })
    assert dumbService.dumb
    assert invocations == 0
    PlatformTestUtil.waitWithEventsDispatching("dumbService.queueTask didn't complete in 5 seconds", { latch.count == 0 }, 5)
    assert invocations == 1
  }

  private DumbServiceImpl getDumbService() {
    (DumbServiceImpl)DumbService.getInstance(project)
  }

  @Test
  void "test no deadlocks when indexing JSP modally"() {
    def tempFixture = new TempDirTestFixtureImpl()
    Disposer.register(disposable) { tempFixture.tearDown() }

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
      dumbService.runInDumbMode {
        CountDownLatch waiting = new CountDownLatch(1)
        future = ApplicationManager.getApplication().executeOnPooledThread({
            waiting.countDown()
            dumbService.waitForSmartMode()
          } as Runnable)
        waiting.await()
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

  @Test
  void "test cancelAllTasksAndWait cancels all the tasks submitted via queueTask from other threads with no race"() {
    DumbServiceImpl dumbService = getDumbService()
    AtomicBoolean queuedTaskInvoked = new AtomicBoolean(false)
    AtomicBoolean dumbTaskFinished = new AtomicBoolean(false)

    Thread t1 = new Thread({
                             dumbService.queueTask(
                               new DumbModeTask() {
                                 @Override
                                 void performInDumbMode(@NotNull ProgressIndicator indicator) {
                                   queuedTaskInvoked.set(true)
                                 }

                                 @Override
                                 void dispose() {
                                   dumbTaskFinished.set(true)
                                 }
                               }
                             )
                           }, "Test thread 1")

    // we are on Write thread without write action
    t1.start()
    PlatformTestUtil.waitWithEventsDispatching("dumbService.queueTask didn't complete in 5 seconds", { !t1.isAlive() }, 5)
    assertFalse("Thread should have completed", t1.isAlive())

    // this should also cancel the task submitted by t1. There is no race: t1 definitely submitted this task and the thread itself finished.
    dumbService.cancelAllTasksAndWait()

    PlatformTestUtil.waitWithEventsDispatching("DumbModeTask didn't complete in 5 seconds", dumbTaskFinished::get, 5)

    assertTrue(dumbTaskFinished.get())
    assertFalse(queuedTaskInvoked.get())
  }
}
