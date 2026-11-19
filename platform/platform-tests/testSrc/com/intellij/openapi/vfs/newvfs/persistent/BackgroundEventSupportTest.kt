// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BackgroundEventSupport
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import com.intellij.util.ui.EDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createTempFile

@TestApplication
class BackgroundEventSupportTest {
  @Test
  fun `core VFS event reaches both topics once`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val journal = subscribe(disposable)
    val file = VirtualFileManager.getInstance().findFileByNioPath(createTempFile())!!

    edtWriteAction {
      file.rename(this, "${file.name}-renamed")
    }

    assertThat(journal).containsExactly("bg.before", "edt.before", "edt.after", "bg.after")
  }

  @Test
  fun `event sent only to VFS_CHANGES reaches VFS_CHANGES_BG listeners once`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val journal = subscribe(disposable)
    val file = VirtualFileManager.getInstance().findFileByNioPath(createTempFile())!!

    val loggedErrors = collectLoggedErrors {
      edtWriteAction {
        val events = newEvents(file)
        val publisher = application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES)
        publisher.before(events)
        publisher.after(events)
      }
    }

    assertThat(journal).containsExactlyInAnyOrder("bg.before", "edt.before", "edt.after", "bg.after")
    // Every .before entry must be recorded before every .after entry.
    val entries = journal.toList()
    assertThat(entries.indexOfLast { it.endsWith(".before") }).isLessThan(entries.indexOfFirst { it.endsWith(".after") })
    assertThat(loggedErrors).hasSize(2).allSatisfy { message ->
      assertThat(message).contains("VirtualFileManager.VFS_CHANGES")
      assertThat(message).contains("fireEvents")
      assertThat(message).doesNotContain("VirtualFileManager.VFS_CHANGES_BG")
    }
  }

  @Test
  fun `marked events are not forwarded to VFS_CHANGES_BG`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val journal = subscribe(disposable)
    val file = VirtualFileManager.getInstance().findFileByNioPath(createTempFile())!!
    val events = newEvents(file)

    BackgroundEventSupport.markBeforeProcessed(events)
    BackgroundEventSupport.markAfterProcessed(events)
    try {
      edtWriteAction {
        val publisher = application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES)
        publisher.before(events)
        publisher.after(events)
      }
    }
    finally {
      BackgroundEventSupport.unmarkBeforeProcessed(events)
      BackgroundEventSupport.unmarkAfterProcessed(events)
    }

    assertThat(journal).containsExactly("edt.before", "edt.after")

    val loggedErrors = collectLoggedErrors {
      edtWriteAction {
        val publisher = application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES)
        publisher.before(events)
        publisher.after(events)
      }
    }

    assertThat(journal).containsExactlyInAnyOrder(
      "edt.before", "edt.after",
      "bg.before", "edt.before", "edt.after", "bg.after",
    )
    assertThat(loggedErrors).hasSize(2)
  }

  @Test
  fun `event sent only to VFS_CHANGES is bridged for each publish`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val journal = subscribe(disposable)
    val file = VirtualFileManager.getInstance().findFileByNioPath(createTempFile())!!

    val loggedErrors = collectLoggedErrors {
      repeat(2) {
        edtWriteAction {
          val events = newEvents(file)
          val publisher = application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES)
          publisher.before(events)
          publisher.after(events)
        }
      }
    }

    assertThat(journal).containsExactlyInAnyOrder(
      "bg.before", "edt.before", "edt.after", "bg.after",
      "bg.before", "edt.before", "edt.after", "bg.after",
    )
    assertThat(loggedErrors).hasSize(4)
  }

  @Test
  fun `event sent directly to VFS_CHANGES_BG logs error with duplicate warning`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val journal = subscribe(disposable)
    val file = VirtualFileManager.getInstance().findFileByNioPath(createTempFile())!!

    val loggedErrors = collectLoggedErrors {
      backgroundWriteAction {
        val events = newEvents(file)
        val publisher = application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES_BG)
        publisher.before(events)
        publisher.after(events)
      }
    }

    assertThat(journal).containsExactly("bg.before", "bg.after")
    assertThat(loggedErrors).hasSize(2).allSatisfy { message ->
      assertThat(message).contains("VirtualFileManager.VFS_CHANGES_BG")
      assertThat(message).contains("fireEvents")
      assertThat(message).containsIgnoringCase("duplicate")
    }
  }

  private suspend fun collectLoggedErrors(action: suspend () -> Unit): List<String> {
    val messages = ConcurrentLinkedQueue<String>()
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        messages.add(message)
        return Action.NONE
      }
    }).use {
      action()
    }
    return messages.toList()
  }

  private fun subscribe(disposable: Disposable): Collection<String> {
    val journal = ConcurrentLinkedQueue<String>()
    application.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>) {
        assertThat(EDT.isCurrentThreadEdt()).isTrue
        journal.add("edt.before")
      }

      override fun after(events: List<VFileEvent>) {
        assertThat(EDT.isCurrentThreadEdt()).isTrue
        journal.add("edt.after")
      }
    })
    application.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListenerBackgroundable {
      override fun before(events: List<VFileEvent>) {
        assertThat(EDT.isCurrentThreadEdt()).isFalse
        journal.add("bg.before")
      }

      override fun after(events: List<VFileEvent>) {
        assertThat(EDT.isCurrentThreadEdt()).isFalse
        journal.add("bg.after")
      }
    })
    return journal
  }

  private fun newEvents(file: VirtualFile): List<VFileEvent> {
    return listOf(VFilePropertyChangeEvent(this, file, VirtualFile.PROP_NAME, file.name, "${file.name}-renamed"))
  }
}