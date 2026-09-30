// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.RecentFileHistoryOrderListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ToolWindowEditorTabFileTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)

  private val project: Project get() = projectFixture.get()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  private fun createFile(presentation: ToolWindowEditorTabPresentation): ToolWindowEditorTabFile {
    return createFile(flowOf(presentation))
  }

  private fun createFile(presentationFlow: Flow<ToolWindowEditorTabPresentation>): ToolWindowEditorTabFile {
    return createTabFile(
      project = project,
      toolWindowId = "TestToolWindow",
      presentationFlow = presentationFlow,
    )
  }

  /**
   * A buffered channel seeded with [initial]. Exposed to the file as a cold flow via [receiveAsFlow];
   * the test drives subsequent presentations with [Channel.trySend].
   */
  private fun presentationChannel(initial: ToolWindowEditorTabPresentation): Channel<ToolWindowEditorTabPresentation> {
    return Channel<ToolWindowEditorTabPresentation>(capacity = Channel.UNLIMITED).also { it.trySend(initial) }
  }

  /**
   * Records the files that the sessions of this project report to Recent Files.
   */
  private fun recordRecentFileUpdates(): List<VirtualFile> {
    val updatedFiles = mutableListOf<VirtualFile>()
    project.messageBus.connect(disposable).subscribe(RecentFileHistoryOrderListener.TOPIC, object : RecentFileHistoryOrderListener {
      override fun recentFileUpdated(file: VirtualFile) {
        updatedFiles += file
      }
    })
    return updatedFiles
  }

  private suspend fun awaitPresentation(
    file: ToolWindowEditorTabFile,
    title: String,
    icon: Icon?,
  ) {
    withTimeout(10.seconds) {
      while (file.tabTitle(project) != title || file.tabIcon(project) != icon) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        delay(20.milliseconds)
      }
    }
  }

  @Test
  fun `file exposes tool window tab defaults`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabPresentation("Title", AllIcons.General.Gear))
    awaitPresentation(file, title = "Title", icon = AllIcons.General.Gear)

    assertThat(file.tabTitle(project)).isEqualTo("Title")
    assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Gear)
    assertThat(file.isWritable).isFalse()
    // A tab file stays read-only.
    assertThatThrownBy { file.isWritable = true }.isInstanceOf(UnsupportedOperationException::class.java)
    assertThat(file.isWritable).isFalse()
    assertThat(file.isValid).isTrue()
    assertThat(file.isIncludedInEditorHistory(project)).isTrue()
    assertThat(file.isPersistedInEditorHistory()).isFalse()
    // Tool window tabs must never be split.
    assertThat(file.getUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT)).isTrue()
  }

  @Test
  fun `presentation updates the last known icon`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabPresentation("Title", AllIcons.General.Gear))
    awaitPresentation(file, title = "Title", icon = AllIcons.General.Gear)

    assertThat(file.lastKnownIcon).isEqualTo(AllIcons.General.Gear)
  }

  @Test
  fun `a persistent file stores its last known icon in the path`(@TempDir tempDir: Path): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = PersistentToolWindowEditorTabPath(project.locationHash, "TestToolWindow", "icon-tab", "Name")
      val file = ToolWindowEditorTabFile(toolWindowId = "TestToolWindow", persistentPath = path)
      val icon = createSerializableIcon(tempDir)
      val expectedIcon = requireNotNull(icon.serialized())

      file.updateIcon(icon)

      val storedPath = requireNotNull(PersistentToolWindowEditorTabPath.parse(requireNotNull(file.persistentPath).toString()))
      assertThat(storedPath.icon).isEqualTo(expectedIcon)
      assertThat(storedPath.name).isEqualTo("Name")
      val restoredFile = ToolWindowEditorTabFile(toolWindowId = "TestToolWindow", persistentPath = storedPath)
      assertThat(restoredFile.lastKnownIcon.serialized()).isEqualTo(expectedIcon)
    }

  @Test
  fun `a persistent file belongs to the editor history of its own project only`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val ownFile = ToolWindowEditorTabFile(
        toolWindowId = "TestToolWindow",
        persistentPath = PersistentToolWindowEditorTabPath(project.locationHash, "TestToolWindow", "own-tab", "Own"),
      )
      val foreignFile = ToolWindowEditorTabFile(
        toolWindowId = "TestToolWindow",
        persistentPath = PersistentToolWindowEditorTabPath("other-project-hash", "TestToolWindow", "foreign-tab", "Foreign"),
      )

      assertThat(ownFile.isIncludedInEditorHistory(project)).isTrue()
      assertThat(ownFile.isPersistedInEditorHistory()).isTrue()
      // The application-level file system can resolve the tab of another project. Recent Files must not show it here.
      assertThat(foreignFile.isIncludedInEditorHistory(project)).isFalse()
    }

  @Test
  fun `closing the tab invalidates the file`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabPresentation("Title"))
    assertThat(file.isValid).isTrue()

    ToolWindowEditorTabManager.getInstance(project).closeEditorTabFile(file, releaseContent = true)

    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `closing the tab to reopen it keeps the file valid`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile(ToolWindowEditorTabPresentation("Title"))
      // Set by the "return to tool window" path: the file is closed only to be moved, not invalidated.
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)

      ToolWindowEditorTabManager.getInstance(project).closeEditorTabFile(file, releaseContent = false)

      assertThat(file.isValid).isTrue()
    }

  @Test
  fun `invalidate marks the file invalid`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabPresentation("Title"))

    file.invalidate()

    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `presentation flow updates the tab icon without renaming the file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val presentations = presentationChannel(ToolWindowEditorTabPresentation("Stable title", AllIcons.General.Gear))
      val file = createFile(presentations.receiveAsFlow())
      // Open in the editor so the presentation-update path runs against a real open composite.
      manager.openFile(file, true)
      awaitPresentation(file, title = "Stable title", icon = AllIcons.General.Gear)
      assertThat(file.tabTitle(project)).isEqualTo("Stable title")
      assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Gear)

      presentations.trySend(ToolWindowEditorTabPresentation("Stable title", AllIcons.General.Add))
      awaitPresentation(file, title = "Stable title", icon = AllIcons.General.Add)
      assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Add)
      assertThat(file.tabTitle(project)).isEqualTo("Stable title")
    }

  @Test
  fun `presentation flow renames the file on a title change`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val presentations = presentationChannel(ToolWindowEditorTabPresentation("Initial title", AllIcons.General.Gear))
      val file = createFile(presentations.receiveAsFlow())
      awaitPresentation(file, title = "Initial title", icon = AllIcons.General.Gear)
      assertThat(file.tabTitle(project)).isEqualTo("Initial title")

      // Only the title changes: this exercises the rename branch of applyPresentation.
      presentations.trySend(ToolWindowEditorTabPresentation("Renamed title", AllIcons.General.Gear))
      awaitPresentation(file, title = "Renamed title", icon = AllIcons.General.Gear)
      assertThat(file.tabTitle(project)).isEqualTo("Renamed title")
      // The icon is unchanged across the rename.
      assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Gear)
    }

  @Test
  fun `the session publishes a recent file update only for a changed presentation`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val updatedFiles = recordRecentFileUpdates()
      val presentations = presentationChannel(ToolWindowEditorTabPresentation("Title", AllIcons.General.Gear))
      val file = createFile(presentations.receiveAsFlow())
      awaitPresentation(file, title = "Title", icon = AllIcons.General.Gear)

      presentations.trySend(ToolWindowEditorTabPresentation("Title", AllIcons.General.Gear))
      presentations.trySend(ToolWindowEditorTabPresentation("Renamed", AllIcons.General.Gear))
      awaitPresentation(file, title = "Renamed", icon = AllIcons.General.Gear)

      // The flow is ordered, so the equal presentation was handled before the rename. Only the initial
      // presentation and the rename may reorder Recent Files.
      assertThat(updatedFiles).containsExactly(file, file)
    }

  @Test
  fun `a closed session ignores later presentations`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val updatedFiles = recordRecentFileUpdates()
      val presentations = presentationChannel(ToolWindowEditorTabPresentation("Title", AllIcons.General.Gear))
      val file = createFile(presentations.receiveAsFlow())
      awaitPresentation(file, title = "Title", icon = AllIcons.General.Gear)
      val updatesBeforeClose = updatedFiles.size

      ToolWindowEditorTabManager.getInstance(project).closeEditorTabFile(file, releaseContent = true)
      presentations.trySend(ToolWindowEditorTabPresentation("Renamed", AllIcons.General.Add))
      repeat(5) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        delay(20.milliseconds)
      }

      assertThat(file.name).isEqualTo("Title")
      assertThat(file.lastKnownIcon).isEqualTo(AllIcons.General.Gear)
      assertThat(updatedFiles).hasSize(updatesBeforeClose)
    }

  @Test
  fun `presentation flow updates the title and icon together`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val presentations = presentationChannel(ToolWindowEditorTabPresentation("Initial title", AllIcons.General.Gear))
      val file = createFile(presentations.receiveAsFlow())
      // Open in the editor so the presentation-update path runs against a real open composite.
      manager.openFile(file, true)
      awaitPresentation(file, title = "Initial title", icon = AllIcons.General.Gear)
      assertThat(file.tabTitle(project)).isEqualTo("Initial title")
      assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Gear)

      // A single presentation update changes both the title and the icon at once.
      presentations.trySend(ToolWindowEditorTabPresentation("Renamed title", AllIcons.General.Add))
      awaitPresentation(file, title = "Renamed title", icon = AllIcons.General.Add)
      assertThat(file.tabTitle(project)).isEqualTo("Renamed title")
      assertThat(file.tabIcon(project)).isEqualTo(AllIcons.General.Add)
    }
}
