// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ToolWindowEditorTabFileTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)

  private val project: Project get() = projectFixture.get()
  private val projectScope: CoroutineScope get() = (project as ComponentManagerEx).getCoroutineScope()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  private fun createFile(descriptor: ToolWindowEditorTabDescriptor): ToolWindowEditorTabFile {
    return ToolWindowEditorTabFile(
      descriptorFlow = MutableStateFlow(descriptor),
      toolWindowId = "TestToolWindow",
      component = JPanel(),
      preferredFocusedComponent = JPanel(),
      content = createTabContent(),
      project = project,
      parentCoroutineScope = projectScope,
    )
  }

  @Test
  fun `file exposes tool window tab defaults`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabDescriptor("Title", AllIcons.General.Gear))

    assertThat(file.name).isEqualTo("Title")
    assertThat(file.tabIcon).isEqualTo(AllIcons.General.Gear)
    assertThat(file.isWritable).isTrue()
    assertThat(file.isValid).isTrue()
    assertThat(file.isIncludedInEditorHistory(project)).isTrue()
    assertThat(file.isPersistedInEditorHistory()).isFalse()
    // Tool window tabs must never be split.
    assertThat(file.getUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT)).isTrue()
  }

  @Test
  fun `onEditorClosed invalidates the file`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabDescriptor("Title"))
    assertThat(file.isValid).isTrue()

    file.onEditorClosed()

    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `onEditorClosed keeps the file valid when closing to reopen`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile(ToolWindowEditorTabDescriptor("Title"))
      // Set by the "return to tool window" path: the file is closed only to be moved, not invalidated.
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)

      file.onEditorClosed()

      assertThat(file.isValid).isTrue()
    }

  @Test
  fun `invalidateEditorTabFile marks the file invalid`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile(ToolWindowEditorTabDescriptor("Title"))

    file.invalidateEditorTabFile()

    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `descriptor flow updates the tab icon without renaming the file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val flow = MutableStateFlow(ToolWindowEditorTabDescriptor("Stable title", AllIcons.General.Gear))
      val file = ToolWindowEditorTabFile(
        descriptorFlow = flow,
        toolWindowId = "TestToolWindow",
        component = JPanel(),
        preferredFocusedComponent = JPanel(),
        content = createTabContent(),
        project = project,
        parentCoroutineScope = projectScope,
      )
      // Open in the editor so the presentation-update path runs against a real open composite.
      manager.openFile(file, true)
      assertThat(file.name).isEqualTo("Stable title")
      assertThat(file.tabIcon).isEqualTo(AllIcons.General.Gear)

      flow.value = ToolWindowEditorTabDescriptor("Stable title", AllIcons.General.Add)

      withTimeout(10.seconds) {
        while (file.tabIcon != AllIcons.General.Add) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
          delay(20.milliseconds)
        }
      }
      assertThat(file.tabIcon).isEqualTo(AllIcons.General.Add)
      assertThat(file.name).isEqualTo("Stable title")
    }

  @Test
  fun `descriptor flow renames the file on a title change`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val flow = MutableStateFlow(ToolWindowEditorTabDescriptor("Initial title", AllIcons.General.Gear))
      val file = ToolWindowEditorTabFile(
        descriptorFlow = flow,
        toolWindowId = "TestToolWindow",
        component = JPanel(),
        preferredFocusedComponent = JPanel(),
        content = createTabContent(),
        project = project,
        parentCoroutineScope = projectScope,
      )
      assertThat(file.name).isEqualTo("Initial title")

      // Only the title changes: this exercises the rename branch of updatePresentation.
      flow.value = ToolWindowEditorTabDescriptor("Renamed title", AllIcons.General.Gear)

      withTimeout(10.seconds) {
        while (file.name != "Renamed title") {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
          delay(20.milliseconds)
        }
      }
      assertThat(file.name).isEqualTo("Renamed title")
      // The icon is unchanged across the rename.
      assertThat(file.tabIcon).isEqualTo(AllIcons.General.Gear)
    }

  @Test
  fun `descriptor flow updates the title and icon together`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val flow = MutableStateFlow(ToolWindowEditorTabDescriptor("Initial title", AllIcons.General.Gear))
      val file = ToolWindowEditorTabFile(
        descriptorFlow = flow,
        toolWindowId = "TestToolWindow",
        component = JPanel(),
        preferredFocusedComponent = JPanel(),
        content = createTabContent(),
        project = project,
        parentCoroutineScope = projectScope,
      )
      // Open in the editor so the presentation-update path runs against a real open composite.
      manager.openFile(file, true)
      assertThat(file.name).isEqualTo("Initial title")
      assertThat(file.tabIcon).isEqualTo(AllIcons.General.Gear)

      // A single descriptor update changes both the title and the icon at once.
      flow.value = ToolWindowEditorTabDescriptor("Renamed title", AllIcons.General.Add)

      withTimeout(10.seconds) {
        while (file.name != "Renamed title" || file.tabIcon != AllIcons.General.Add) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
          delay(20.milliseconds)
        }
      }
      assertThat(file.name).isEqualTo("Renamed title")
      assertThat(file.tabIcon).isEqualTo(AllIcons.General.Add)
    }
}
