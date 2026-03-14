// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.history.ActivityId
import com.intellij.history.FileRevisionTimestampComparator
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.xdebugger.hotswap.SourceFileChangesCollector
import com.intellij.xdebugger.hotswap.SourceFileChangesListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val CONTENT = """
      line 1
      line 2
    """.trimIndent()

@TestApplication
class SourceFileChangesCollectorImplTest {
  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @BeforeEach
  fun setUp() {
    SourceFileChangesCollectorImpl.customLocalHistory = null
    deleteTestFileIfExists()
  }

  @AfterEach
  fun tearDown() {
    SourceFileChangesCollectorImpl.customLocalHistory = null
    deleteTestFileIfExists()
  }

  private fun doTest(content: String = CONTENT, fileName: String = "a.txt", test: suspend (CoroutineScope, Document) -> Unit) {
    val file = createTestFile(fileName, content)
    try {
      runBlocking {
        val document = requireNotNull(ReadAction.computeBlocking<Document?, RuntimeException> { FileDocumentManager.getInstance().getDocument(file) })
        test(this, document)
      }
    }
    finally {
      deleteTestFileIfExists(fileName)
    }
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  fun testRevertChanges() {
    doTest { scope, document ->
      scope.withCollector { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertEquals(Response.NEW_CHANGES, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        writeCommandAction(project, "Replace first line back") { document.replaceString(0, 6, "line") }
        assertEquals(Response.CHANGES_CANCELED, channel.receive())
        assertTrue(collector.getChanges().isEmpty())
      }
    }
  }

  private fun createTestFile(fileName: String, content: String): VirtualFile =
    WriteCommandAction.writeCommandAction(project).compute<VirtualFile, RuntimeException> {
      sourceRoot.findChild(fileName)?.let { existingFile ->
        if (existingFile.isValid) {
          existingFile.delete(this@SourceFileChangesCollectorImplTest)
        }
      }
      sourceRoot.createChildData(this@SourceFileChangesCollectorImplTest, fileName).also {
        it.setBinaryContent(content.toByteArray())
      }
  }

  private fun deleteTestFileIfExists(fileName: String = "a.txt") {
    val file = ReadAction.computeBlocking<VirtualFile?, RuntimeException> { sourceRoot.findChild(fileName) } ?: return
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      if (file.isValid) {
        file.delete(this@SourceFileChangesCollectorImplTest)
      }
    }
  }
}

private inline fun CoroutineScope.withCollector(
  vararg filters: SourceFileChangeFilter<VirtualFile>,
  action: (SourceFileChangesCollector<VirtualFile>, channel: ReceiveChannel<Response>) -> Unit,
) {
  val channel = Channel<Response>()
  val collector = SourceFileChangesCollectorImpl(this, MockListener(this, channel), *filters)
  action(collector, channel)
  Disposer.dispose(collector)
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
  override val isEnabled: Boolean = true

  override fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray? = bytes
  override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction = LocalHistoryAction.NULL
  override fun putEventLabel(project: Project, name: String, activityId: ActivityId): Label = Label.NULL_INSTANCE
  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label = Label.NULL_INSTANCE
  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label = Label.NULL_INSTANCE
  override fun isUnderControl(file: VirtualFile): Boolean = false
}
