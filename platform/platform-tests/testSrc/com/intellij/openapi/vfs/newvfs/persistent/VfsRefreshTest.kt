// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

@TestApplication
class VfsRefreshTest {

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `suspending refresh calls listeners on background threads`(): Unit = bulkFileListenerTestStub(false) { virtualFile ->
    RefreshQueue.getInstance().refresh(false, listOf(virtualFile))
  }

  @Test
  fun `regular synchronous refresh calls listeners on EDT threads`(): Unit = bulkFileListenerTestStub(true) { virtualFile ->
    RefreshQueue.getInstance().refresh(false, false, null, listOf(virtualFile))
  }
  @Test
  fun `parallelization guard stress test`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val map = ConcurrentHashMap<Any, Pair<Semaphore, Int>>()
    val numberOfKeys = 10
    val arrayOfKeys = AtomicReferenceArray(Array(numberOfKeys) { Any() })
    val arrayOfAtomicRefs = AtomicReferenceArray(Array(numberOfKeys) { AtomicInteger(0) })
    coroutineScope {
      repeat(1000) { id ->
        val identifier = id % numberOfKeys
        launch {
          RefreshQueueImpl.executeWithParallelizationGuard(arrayOfKeys[identifier], map) {
            val counter = arrayOfAtomicRefs[identifier].incrementAndGet()
            assertThat(counter).isEqualTo(1)
            arrayOfAtomicRefs[identifier].decrementAndGet()
          }
        }
      }
    }
    assertThat(map).isEmpty()
  }

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `suspending refresh calls async listeners on background threads`(): Unit = asyncFileListenerTestStub(false) { virtualFile ->
    RefreshQueue.getInstance().refresh(false, listOf(virtualFile))
  }

  @Test
  fun `regular synchronous refresh calls async listeners on EDT`(): Unit = asyncFileListenerTestStub(true) { virtualFile ->
    RefreshQueue.getInstance().refresh(false, false, null, listOf(virtualFile))
  }

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `suspending vfs refresh is cancellable`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext.job)
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener {
      job.complete()
      while (true) {
        ProgressManager.checkCanceled()
      }
      null
    }, disposable)
    val refreshJob = launch(Dispatchers.Default) {
      runRefreshOnADirtyFile {
        RefreshQueue.getInstance().refresh(false, listOf(it))
      }
    }
    job.join()
    delay(1000)
    refreshJob.cancelAndJoin()
  }

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `suspending vfs refresh uses modality of its context`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
      val currentModality = coroutineContext.contextModality()
      assertThat(currentModality).isNotEqualTo(ModalityState.nonModal())
      val refreshStarted = AtomicBoolean(false)
      VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener {
        assertThat(ModalityState.defaultModalityState()).isEqualTo(currentModality)
        refreshStarted.set(true)
        null
      }, disposable)

      runRefreshOnADirtyFile {
        RefreshQueue.getInstance().refresh(true, listOf(it))
      }
      assertThat(refreshStarted.get()).isTrue()
    }
  }


  fun refreshTestStub(createListeners: (Disposable, AtomicInteger) -> Unit, refreshFunction: suspend (VirtualFile) -> Unit, check: (AtomicInteger) -> Unit): Unit = timeoutRunBlocking {
    val disposable = Disposer.newDisposable()
    try {
      val counter = AtomicInteger(0)
      createListeners(disposable, counter)

     runRefreshOnADirtyFile(refreshFunction)

      check(counter)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun runRefreshOnADirtyFile(refresh: suspend (VirtualFile) -> Unit) {
    val file = createTempFile()
    try {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file)!!
      file.write("42")
      refresh(virtualFile)
    }
    finally {
      file.delete()
    }
  }

  fun bulkFileListenerTestStub(bgListenersShouldRunOnEdt: Boolean, refresh: suspend (VirtualFile) -> Unit) = refreshTestStub(
    { disposable, counter ->
      application.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun before(events: List<VFileEvent>) {
          assertThat(EDT.isCurrentThreadEdt()).isTrue
          counter.incrementAndGet()
        }

        override fun after(events: List<VFileEvent>) {
          assertThat(EDT.isCurrentThreadEdt()).isTrue
          counter.incrementAndGet()
        }
      })
      application.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListenerBackgroundable {
        override fun before(events: List<VFileEvent>) {
          assertThat(EDT.isCurrentThreadEdt()).isEqualTo(bgListenersShouldRunOnEdt)
          counter.incrementAndGet()
        }

        override fun after(events: List<VFileEvent>) {
          assertThat(EDT.isCurrentThreadEdt()).isEqualTo(bgListenersShouldRunOnEdt)
          counter.incrementAndGet()
        }
      })
    }, refresh, { counter ->
      assertThat(counter.get()).isEqualTo(4)
    })


  fun asyncFileListenerTestStub(bgListenersShouldRunOnEdt: Boolean, refresh: suspend (VirtualFile) -> Unit) = refreshTestStub(
    { disposable, counter ->
      VirtualFileManager.getInstance().addAsyncFileListener(
        {
          object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
              assertThat(EDT.isCurrentThreadEdt()).isTrue
              counter.incrementAndGet()
            }

            override fun afterVfsChange() {
              assertThat(EDT.isCurrentThreadEdt()).isTrue
              counter.incrementAndGet()
            }
          }
        }, disposable)
      VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable(
        {
          object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
              assertThat(EDT.isCurrentThreadEdt()).isEqualTo(bgListenersShouldRunOnEdt)
              counter.incrementAndGet()
            }

            override fun afterVfsChange() {
              assertThat(EDT.isCurrentThreadEdt()).isEqualTo(bgListenersShouldRunOnEdt)
              counter.incrementAndGet()
            }
          }
        }, disposable)

    }, refresh, { counter ->
      assertThat(counter.get()).isEqualTo(4)
    })

  @Test
  fun `no write action if file was not changed`() = timeoutRunBlocking {
    val file = createTempFile()
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file)!!
    writeAction {
      virtualFile.writeText("42")
    }
    virtualFile.refresh(false, false)
    val currentCounter = readAction {
      AsyncExecutionServiceImpl.getWriteActionCounter()
    }
    RefreshQueue.getInstance().refresh(false, listOf(virtualFile))
    val newCounter = readAction {
      AsyncExecutionServiceImpl.getWriteActionCounter()
    }
    assertEquals(currentCounter, newCounter, "There should be no write action if there was nothing to refresh")
  }
}