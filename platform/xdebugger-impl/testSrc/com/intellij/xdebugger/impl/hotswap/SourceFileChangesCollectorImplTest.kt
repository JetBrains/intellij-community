// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.history.ActivityId
import com.intellij.history.FileRevisionTimestampComparator
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
        val document = requireNotNull(readAction { FileDocumentManager.getInstance().getDocument(file) })
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
        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        writeCommandAction(project, "Replace second line") { document.replaceString(10, 14, "string") }
        assertEquals(Response.NewChanges, channel.receive())
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
        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        collector.resetChanges()
        assertNull(channel.tryReceive().getOrNull())
        assertTrue(collector.getChanges().isEmpty())

        writeCommandAction(project, "Replace second line") { document.replaceString(10, 14, "string") }
        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  @Test
  fun testFiltering() {
    doTest { scope, document ->
      val filter = SourceFileChangeFilter<VirtualFile> { false }
      scope.withCollector(filters = listOf(filter)) { collector, channel ->
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
        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)

        writeCommandAction(project, "Replace first line back") { document.replaceString(0, 6, "line") }
        assertEquals(Response.ChangesCanceled, channel.receive())
        assertTrue(collector.getChanges().isEmpty())
      }
    }
  }

  @Test
  fun testCompatibilityCheckerReceivesSourceFileChangeWithOldContent() {
    doTest { scope, document ->
      val changes = Channel<SourceFileChange>(Channel.UNLIMITED)
      val checker = SourceFileChangeCompatibilityChecker { change ->
        changes.send(change)
        HotSwapChangesCompatibility.Compatible
      }
      scope.withCollector(compatibilityCheckers = listOf(checker)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }

        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
        val change = changes.receive()
        assertEquals("a.txt", change.file.name)
        assertEquals(CONTENT, change.oldContent.toString())
      }
    }
  }

  @Test
  fun testIncompatibleChanges() {
    val reason = "Method was added"
    doTest { scope, document ->
      val checker = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Incompatible(reason) }
      scope.withCollector(compatibilityCheckers = listOf(checker)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }

        assertEquals(Response.IncompatibleChanges(reason), channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  @Test
  fun testIrrelevantCompatibilityResultIsIgnored() {
    doTest { scope, document ->
      val checker = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Irrelevant }
      scope.withCollector(compatibilityCheckers = listOf(checker)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }

        assertEquals(Response.NewChanges, channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  @Test
  fun testCompatibilityCheckersUseMostSevereResult() {
    val reason = "Method was added"
    doTest { scope, document ->
      val checker1 = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Irrelevant }
      val checker2 = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Incompatible(reason) }
      val checker3 = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Unknown }
      val checker4 = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Compatible }
      scope.withCollector(compatibilityCheckers = listOf(checker1, checker2, checker3, checker4)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }

        assertEquals(Response.IncompatibleChanges(reason), channel.receive())
        assertEquals("a.txt", collector.getChanges().single().name)
      }
    }
  }

  @Test
  fun testIncompatibleChangesCanceledAfterRevert() {
    doTest { scope, document ->
      val checker = SourceFileChangeCompatibilityChecker { HotSwapChangesCompatibility.Incompatible("Method was added") }
      scope.withCollector(compatibilityCheckers = listOf(checker)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(document.text.toByteArray())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        assertEquals(Response.IncompatibleChanges("Method was added"), channel.receive())

        writeCommandAction(project, "Replace first line back") { document.replaceString(0, 6, "line") }
        assertEquals(Response.ChangesCanceled, channel.receive())
        assertTrue(collector.getChanges().isEmpty())
      }
    }
  }

  @Test
  fun testObsoleteDocumentChangeIsSkippedWhenNewerChangeForSameFileArrives() {
    doTest { scope, document ->
      val firstCheckStarted = CompletableDeferred<Unit>()
      val allowFirstCheckToComplete = CompletableDeferred<Unit>()
      val checker = SourceFileChangeCompatibilityChecker {
        firstCheckStarted.complete(Unit)
        allowFirstCheckToComplete.await()
        HotSwapChangesCompatibility.Compatible
      }
      scope.withCollector(compatibilityCheckers = listOf(checker)) { collector, channel ->
        SourceFileChangesCollectorImpl.customLocalHistory = MockLocalHistory(CONTENT.toByteArray())

        writeCommandAction(project, "Replace first line") { document.replaceString(0, 4, "string") }
        firstCheckStarted.await()
        assertTrue(collector.getChanges().isEmpty())
        assertNull(channel.tryReceive().getOrNull())

        writeCommandAction(project, "Replace first line twice") {
          document.replaceString(0, 6, "second")
          document.replaceString(0, 6, "line")
        }
        allowFirstCheckToComplete.complete(Unit)

        assertEquals(Response.ChangesCanceled, channel.receive())
        assertTrue(collector.getChanges().isEmpty())
        assertNull(channel.tryReceive().getOrNull())
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
  filters: List<SourceFileChangeFilter<VirtualFile>> = emptyList(),
  compatibilityCheckers: List<SourceFileChangeCompatibilityChecker> = emptyList(),
  action: (SourceFileChangesCollector<VirtualFile>, channel: ReceiveChannel<Response>) -> Unit,
) {
  val listener = MockListener()
  val collector = SourceFileChangesCollectorImpl(this, listener, filters, compatibilityCheckers)
  try {
    action(collector, listener.channel)
  }
  finally {
    Disposer.dispose(collector)
  }
}

private sealed interface Response {
  data object NewChanges : Response
  data class IncompatibleChanges(val reason: String) : Response
  data object ChangesCanceled : Response
}

private class MockListener : SourceFileChangesListener {
  val channel = Channel<Response>(Channel.UNLIMITED)
  override fun onNewChanges() {
    channel.trySend(Response.NewChanges)
  }

  override fun onIncompatibleChanges(reason: String) {
    channel.trySend(Response.IncompatibleChanges(reason))
  }

  override fun onChangesCanceled() {
    channel.trySend(Response.ChangesCanceled)
  }
}

private class MockLocalHistory(val bytes: ByteArray) : LocalHistory() {
  override val isEnabled: Boolean = true

  override fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray = bytes
  override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction = LocalHistoryAction.NULL
  override fun putEventLabel(project: Project, name: String, activityId: ActivityId): Label = Label.NULL_INSTANCE
  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label = Label.NULL_INSTANCE
  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label = Label.NULL_INSTANCE
  override suspend fun isLabelValid(project: Project, labelId: String): Boolean = false
  override suspend fun revertToLabel(project: Project, labelId: String, file: VirtualFile) = Unit
  override fun isUnderControl(file: VirtualFile): Boolean = false
}
