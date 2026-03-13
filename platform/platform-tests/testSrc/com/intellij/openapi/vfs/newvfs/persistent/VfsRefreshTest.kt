// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.application.impl.concurrencyTest
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.FileContentUtilCore
import com.intellij.util.application
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

@TestApplication
class VfsRefreshTest {

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `suspending refresh calls listeners on background threads`(): Unit = bulkFileListenerTestStub { virtualFile ->
    RefreshQueue.getInstance().refresh(false, listOf(virtualFile))
  }

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `regular synchronous refresh calls listeners on background threads`(): Unit = bulkFileListenerTestStub { virtualFile ->
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
  fun `suspending refresh calls async listeners on background threads`(): Unit = asyncFileListenerTestStub { virtualFile ->
    RefreshQueue.getInstance().refresh(false, listOf(virtualFile))
  }

  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "true")
  fun `regular synchronous refresh calls async listeners on background`(): Unit = asyncFileListenerTestStub { virtualFile ->
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

  fun bulkFileListenerTestStub(refresh: suspend (VirtualFile) -> Unit) = refreshTestStub(
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
          assertThat(EDT.isCurrentThreadEdt()).isFalse
          counter.incrementAndGet()
        }

        override fun after(events: List<VFileEvent>) {
          assertThat(EDT.isCurrentThreadEdt()).isFalse
          counter.incrementAndGet()
        }
      })
    }, refresh, { counter ->
      assertThat(counter.get()).isEqualTo(4)
    })


  fun asyncFileListenerTestStub(refresh: suspend (VirtualFile) -> Unit) = refreshTestStub(
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
              assertThat(EDT.isCurrentThreadEdt()).isFalse
              counter.incrementAndGet()
            }

            override fun afterVfsChange() {
              assertThat(EDT.isCurrentThreadEdt()).isFalse
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

  @Test
  fun `synchronous refresh in a background write action can terminate successfully`() = timeoutRunBlocking {
    val file = createTempFile()
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file)!!
    writeAction {
      virtualFile.writeText("42")
    }
    backgroundWriteAction {
      virtualFile.refresh(false, false)
    }
    // if this test terminates, there was no hanging
  }

  @Test
  fun `listeners in programmatic reparse run on EDT`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = createTempFile()
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file)!!
    val counter = AtomicInteger(0)

    writeAction {
      virtualFile.writeText("42")
    }

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

    backgroundWriteAction {
      FileContentUtilCore.reparseFiles(virtualFile)
    }
    assertThat(counter.get()).isEqualTo(2)
  }

  @Test
  fun `event processing can start regardless of active scan`() = concurrencyTest {
    val dir = createTempDirectory()
    dir.resolve("file2").createFile()
    val file2 = createTempFile()
    val virtualFile1 = VirtualFileManager.getInstance().findFileByNioPath(dir)!!
    val virtualFile2 = VirtualFileManager.getInstance().findFileByNioPath(file2)!!

    writeAction {
      virtualFile2.writeText("43")
    }

    RefreshQueueImpl.setTestListener {
      checkpoint(1)
      checkpoint(4)
    }
    try {
      launch(Dispatchers.Default) {
        RefreshQueue.getInstance().refresh(false, listOf(virtualFile1))
      }
      checkpoint(2)
      RefreshQueue.getInstance().processEvents(listOf(VFileContentChangeEvent(Any(), virtualFile2, 0, 1)))
      checkpoint(3)
    } finally {
      RefreshQueueImpl.setTestListener(null)
    }
  }

  @Test
  fun `prioritized vfs refresh does not skip async listeners`(): Unit = timeoutRunBlocking {
    val file = createTempFile()
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file)!!
    val doc = readAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)
    }!!
    assertThat(runReadAction { doc.text }).isEqualTo("")
    file.write("42")
    backgroundWriteAction {
      RefreshQueue.getInstance().createSession(false, false, null).apply {
        addFile(virtualFile)
      }.launch()
    }
    assertThat(runReadAction { doc.text }).isEqualTo("42")
  }
}