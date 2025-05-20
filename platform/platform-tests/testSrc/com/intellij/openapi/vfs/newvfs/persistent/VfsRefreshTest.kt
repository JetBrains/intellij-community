// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import com.intellij.util.application
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.ui.EDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.io.path.createTempFile

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


  fun refreshTestStub(createListeners: (Disposable, AtomicInteger) -> Unit, refreshFunction: suspend (VirtualFile) -> Unit, check: (AtomicInteger) -> Unit): Unit = timeoutRunBlocking {
    val disposable = Disposer.newDisposable()
    try {
      val counter = AtomicInteger(0)
      createListeners(disposable, counter)

      val file = createTempFile()
      try {
        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file)!!
        file.write("42")
        refreshFunction(virtualFile)
      }
      finally {
        file.delete()
      }

      check(counter)
    }
    finally {
      Disposer.dispose(disposable)
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
}