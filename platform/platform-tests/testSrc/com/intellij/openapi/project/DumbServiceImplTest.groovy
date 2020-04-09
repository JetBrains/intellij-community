/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.caches.IndexUpdateRunner
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull

import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author peter
 */
class DumbServiceImplTest extends BasePlatformTestCase {

  void "test runWhenSmart is executed synchronously in smart mode"() {
    int invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assert invocations == 1
  }

  void "test runWhenSmart is executed on EDT without write action"() {
    ApplicationManager.application.assertIsDispatchThread()
    int invocations = 0

    Semaphore semaphore = new Semaphore(1)
    dumbService.queueAsynchronousTask(new DumbModeTask() {
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

    dumbService.queueAsynchronousTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        started.set(true)
        assert !ApplicationManager.application.dispatchThread
        try {
          ProgressIndicatorUtils.withTimeout(20_000) {
            def index = FileBasedIndex.getInstance() as FileBasedIndexImpl
            new IndexUpdateRunner(index, ConcurrencyUtil.newSameThreadExecutorService(), 1)
              .indexFiles(project, [child], indicator)
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
}
