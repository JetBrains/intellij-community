// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.junit.Assert

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author peter
 */
class DumbServiceImplTest extends BasePlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    String key = "idea.force.dumb.queue.tasks"
    String prev = System.setProperty(key, "true")
    disposeOnTearDown {
      SystemProperties.setProperty(key, prev)
    }
  }

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

    Assert.assertEquals(2, disposes.get())
    Assert.assertTrue(Disposer.isDisposed(task1))
    Assert.assertTrue(Disposer.isDisposed(task2))
  }

  void "test queueTask is async"() {
    def semaphore = new Semaphore(1)
    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        def e = new Exception()
        for (StackTraceElement element : e.stackTrace) {
          if (element.toString().contains(DumbServiceGuiTaskQueue.class.simpleName)) {
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

  void "test runWhenSmart is executed synchronously in smart mode"() {
    int invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assert invocations == 1
  }

  void "test runWhenSmart is executed on EDT without write action"() {
    ApplicationManager.application.assertIsDispatchThread()
    int invocations = 0

    Semaphore semaphore = new Semaphore(1)
    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        assert !ApplicationManager.application.dispatchThread
        edt {
          dumbService.runWhenSmart {
            invocations++
            ApplicationManager.application.assertIsDispatchThread()
            assert !ApplicationManager.application.writeAccessAllowed
          }
        }
        TimeoutUtil.sleep(100)
        semaphore.up()
      }
    })
    assert dumbService.dumb
    assert invocations == 0
    UIUtil.dispatchAllInvocationEvents()
    assert semaphore.waitFor(1000)
    UIUtil.dispatchAllInvocationEvents()
    assert invocations == 1
  }

  private DumbServiceImpl getDumbService() {
    (DumbServiceImpl)DumbService.getInstance(project)
  }

  void "test no deadlocks when indexing JSP modally"() {
    def tempFixture = new TempDirTestFixtureImpl()
    disposeOnTearDown { tempFixture.tearDown() }

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
    assert ((PsiManagerImpl)psiManager).fileManager.getCachedPsiFile(child) == null

    def started = new AtomicBoolean()
    def finished = new AtomicBoolean()

    dumbService.queueTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        started.set(true)
        assert !ApplicationManager.application.dispatchThread
        try {
          ProgressIndicatorUtils.withTimeout(20_000) {
            def index = FileBasedIndex.getInstance() as FileBasedIndexImpl
            new IndexUpdateRunner(index, ConcurrencyUtil.newSameThreadExecutorService(), 1)
              .indexFiles(project, Collections.singletonList(new IndexUpdateRunner.FileSet(project, "child", [child])),
                          indicator, new ProjectIndexingHistoryImpl(getProject(), "Testing", false))
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

  void testDelayBetweenBecomingSmartAndWaitForSmartReturnMustBeSmall() {
    int N = 100
    int[] delays = new int[N]
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
    int avg = ArrayUtil.averageAmongMedians(delays, 3)
    assert avg == 0 : "Seems there's is a significant delay between becoming smart and waitForSmartMode() return. Delays in ms:\n"+Arrays.toString(delays)+"\n"
  }
}
