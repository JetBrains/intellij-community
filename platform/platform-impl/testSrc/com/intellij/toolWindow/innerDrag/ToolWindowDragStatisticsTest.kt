// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.innerDrag

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
internal class ToolWindowDragStatisticsTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a drag reports the action with the drag input event and the tool window content place`() {
    val events = recordByDrag("TW.SplitAndMoveRight")

    assertThat(events).singleElement().satisfies({
      assertThat(it.event.data["action_id"]).isEqualTo("TW.SplitAndMoveRight")
      assertThat(it.event.data["input_event"]).isEqualTo("MouseLeftDrag")
      assertThat(it.event.data["place"]).isEqualTo("ToolwindowContent")
      assertThat(it.event.data["toolwindow"]).isEqualTo(ToolWindowId.RUN)
    })
  }

  @Test
  fun `each tool window split action can be reported by a drag`() {
    val events = recordByDrag("TW.SplitAndMoveDown", "TW.Unsplit")

    assertThat(events.map { it.event.data["action_id"] }).containsExactly("TW.SplitAndMoveDown", "TW.Unsplit")
  }

  @Test
  fun `an unknown action reports nothing`() {
    assertThat(recordByDrag("TW.NoSuchAction")).isEmpty()
  }

  private fun recordByDrag(vararg actionIds: String): List<LogEvent> {
    val toolWindow = object : MockToolWindow(project) {
      override fun getId(): String = ToolWindowId.RUN
    }
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      for (actionId in actionIds) {
        recordActionByDrag(toolWindow, actionId, JPanel())
      }
    }
    return events.filter { it.group.id == "actions" && it.event.id == "action.finished" }
  }
}
