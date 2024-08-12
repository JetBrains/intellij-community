// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.history.ActivityId
import com.intellij.history.FileRevisionTimestampComparator
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val CONTENT = """
      line 1
      line 2
    """.trimIndent()

class SourceFileChangesCollectorImplTest : LightPlatformCodeInsightTestCase() {
  override fun runInDispatchThread(): Boolean = false
  override fun isRunInCommand(): Boolean = false

  private fun doTest(content: String = CONTENT, fileName: String = "a.txt", test: suspend (CoroutineScope, Document) -> Unit) {
    configureFromFileText(fileName, content)
    runBlocking {
      val document = getDocument(file)!!
      test(this, document)
    }
  }

  fun testChangesDetection() {
    doTest { scope, document ->
      scope.withCollector { collector, channel ->
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        writeCommandAction(project, "Replace second line") { document.replaceString(10, 14, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  fun testResetChanges() {
    doTest { scope, document ->
      scope.withCollector { collector, channel ->
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        collector.resetChanges()
        assertNull(channel.tryReceive().getOrNull())
        assertTrue(collector.getChanges().isEmpty())

        writeCommandAction(project, "Replace second line") { document.replaceString(10, 14, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  fun testFiltering() {
    doTest { scope, document ->
      val filter = SourceFileChangeFilter<VirtualFile> { false }
      scope.withCollector(filter) { collector, channel ->
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertNull(channel.tryReceive().getOrNull())
        assertTrue(collector.getChanges().isEmpty())
      }
    }
  }

  fun testRevertChanges() {
    val disposable = Disposer.newDisposable(testRootDisposable)
    doTest { scope, document ->
      scope.withCollector { collector, channel ->
        (collector as SourceFileChangesCollectorImpl).customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        writeCommandAction(project, "Replace first line back") { document.replaceString(0, 6, "line") }
        assertEquals(Response.CHANGES_CANCELED, channel.receive())
        assertTrue(collector.getChanges().isEmpty())
      }
    }
    Disposer.dispose(disposable)
  }
}

private inline fun CoroutineScope.withCollector(
  vararg filters: SourceFileChangeFilter<VirtualFile>,
  action: (SourceFileChangesCollector<VirtualFile>, channel: ReceiveChannel<Response>) -> Unit,
) {
  val channel = Channel<Response>()
  val collectorScope = childScope("Collector")
  try {
    val collector = SourceFileChangesCollectorImpl(collectorScope, MockListener(this, channel), *filters)
    action(collector, channel)
  }
  finally {
    collectorScope.cancel()
  }
}

private enum class Response {
  NEW_CHANGES, CHANGES_CANCELED
}

private class MockListener(private val scope: CoroutineScope, private val channel: SendChannel<Response>) : SourceFileChangesListener {
  override fun onNewChanges() {
    scope.launch {
      channel.send(Response.NEW_CHANGES)
    }
  }

  override fun onChangesCanceled() {
    scope.launch {
      channel.send(Response.CHANGES_CANCELED)
    }
  }
}

private class MockLocalHistory(val bytes: ByteArray) : LocalHistory() {
  override fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray? = bytes
  override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction = LocalHistoryAction.NULL
  override fun putEventLabel(project: Project, name: String, activityId: ActivityId): Label = Label.NULL_INSTANCE
  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label = Label.NULL_INSTANCE
  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label = Label.NULL_INSTANCE
  override fun isUnderControl(file: VirtualFile): Boolean = false
}
