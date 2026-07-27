// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class ToolWindowEditorTabPreCloseCheckTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )

  private val project: Project get() = projectFixture.get()
  private val projectScope: CoroutineScope get() = (project as ComponentManagerEx).getCoroutineScope()

  private val toolWindowId = "TestToolWindow"

  private fun createTabFile(displayName: String = "tab"): ToolWindowEditorTabFile = ToolWindowEditorTabFile(
    presentationFlow = flowOf(ToolWindowEditorTabPresentation("Title")),
    toolWindowId = toolWindowId,
    component = JPanel(),
    preferredFocusedComponent = JPanel(),
    content = createTabContent(displayName = displayName),
    project = project,
    parentCoroutineScope = projectScope,
  )

  @Test
  fun `non tab files can always be closed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(LightVirtualFile("plain.txt"))).isTrue()
  }

  @Test
  fun `tab file without support can be closed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(createTabFile())).isTrue()
  }

  @Test
  fun `tab file close is delegated to support - allowed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerFakeToolWindowEditorTabSupport(
      toolWindowId,
      FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = true),
      disposable,
    )
    val check = ToolWindowEditorTabPreCloseCheck()
    assertThat(check.canCloseFile(createTabFile())).isTrue()
  }

  @Test
  fun `tab file close is delegated to support - blocked`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerFakeToolWindowEditorTabSupport(
      toolWindowId,
      FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = false),
      disposable,
    )
    val check = ToolWindowEditorTabPreCloseCheck()
    val tabFile = createTabFile()

    assertThat(check.canCloseFile(tabFile)).isFalse()
    // A blocked group vetoes the whole bulk close, even alongside a plain file.
    val plain = LightVirtualFile("plain.txt")
    assertThat(check.canCloseFiles(listOf(plain, tabFile))).isFalse()
    assertThat(check.filterFilesToClose(listOf(plain, tabFile))).containsExactly(plain)
  }

  @Test
  fun `tab files of the same tool window are checked in a single grouped call`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val support = FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Title")), canClose = true)
      registerFakeToolWindowEditorTabSupport(toolWindowId, support, disposable)
      val check = ToolWindowEditorTabPreCloseCheck()
      val tabFile1 = createTabFile()
      val tabFile2 = createTabFile()

      assertThat(check.canCloseFiles(listOf(tabFile1, tabFile2, LightVirtualFile("plain.txt")))).isTrue()
      // The support must be asked once for the whole group, not once per tab.
      assertThat(support.filterTabsToCloseInvocations).hasSize(1)
      assertThat(support.filterTabsToCloseInvocations.single())
        .containsExactly(tabFile1.content, tabFile2.content)
    }

  @Test
  fun `bulk close preserves order while dropping only blocked tab files`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val blockedTab = createTabFile("blocked")
      val closableTab = createTabFile("closable")
      val plain = LightVirtualFile("plain.txt")
      registerFakeToolWindowEditorTabSupport(
        toolWindowId,
        FakeToolWindowEditorTabSupport(
          flowOf(ToolWindowEditorTabPresentation("Title")),
          filterTabsToCloseAction = { listOf(closableTab.content) },
        ),
        disposable,
      )
      val check = ToolWindowEditorTabPreCloseCheck()

      assertThat(check.filterFilesToClose(listOf(blockedTab, plain, closableTab)))
        .containsExactly(plain, closableTab)
      assertThat(check.canCloseFiles(listOf(blockedTab, plain, closableTab))).isFalse()
    }
}
