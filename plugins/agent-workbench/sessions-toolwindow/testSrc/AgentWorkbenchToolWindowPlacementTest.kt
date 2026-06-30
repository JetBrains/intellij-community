// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.thread.view.AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsToolWindowDefaultLayoutExtension
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.toolWindow.DefaultToolWindowDescriptorBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowStripeBuilder
import com.intellij.toolWindow.ToolWindowDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentWorkbenchToolWindowPlacementTest {
  @Test
  fun agentToolWindowsAreRegisteredOnLeftSecondaryStripe() {
    val toolWindows = ToolWindowEP.EP_NAME.extensionList.associateBy { it.id }

    val agentSessions = toolWindows[AGENT_SESSIONS_TOOL_WINDOW_ID]
    assertThat(agentSessions).isNotNull()
    assertThat(agentSessions!!.anchor).isEqualTo(ToolWindowAnchor.LEFT.toString())
    assertThat(agentSessions.secondary).isTrue()

    val threadOutline = toolWindows[AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID]
    assertThat(threadOutline).isNotNull()
    assertThat(threadOutline!!.anchor).isEqualTo(ToolWindowAnchor.LEFT.toString())
    assertThat(threadOutline.secondary).isTrue()
  }

  @Test
  fun agentToolWindowsAreSeededTogetherInDefaultLayouts() {
    assertAgentToolWindowsSeeded { AgentSessionsToolWindowDefaultLayoutExtension().buildV1Layout(it) }
    assertAgentToolWindowsSeeded { AgentSessionsToolWindowDefaultLayoutExtension().buildV2Layout(it) }
  }

  private fun assertAgentToolWindowsSeeded(buildLayout: (DefaultToolWindowLayoutBuilder) -> Unit) {
    val builder = RecordingDefaultToolWindowLayoutBuilder()

    buildLayout(builder)

    assertThat(builder.left.windows.map { it.id })
      .containsExactly(AGENT_SESSIONS_TOOL_WINDOW_ID, AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID)
    assertThat(builder.left.windows.map { it.isSplit })
      .containsExactly(true, true)
    assertThat(builder.left.windows.map { it.weight })
      .containsExactly(0.25f, 0.25f)
    assertThat(builder.right.windows).isEmpty()
    assertThat(builder.bottom.windows).isEmpty()
  }

  @Test
  fun structureViewIsSeededBelowAgentToolWindowsInDefaultLayout() {
    val builder = RecordingDefaultToolWindowLayoutBuilder()
    builder.left.addOrUpdate(ToolWindowId.PROJECT_VIEW)
    builder.left.addOrUpdate(ToolWindowId.COMMIT)
    builder.left.addOrUpdate(ToolWindowId.STRUCTURE_VIEW) {
      isVisible = true
      weight = 0.4f
      contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      isSplit = true
      sideWeight = 0.7f
    }

    AgentSessionsToolWindowDefaultLayoutExtension().buildV2Layout(builder)

    assertThat(builder.left.windows.map { it.id })
      .containsExactly(
        ToolWindowId.PROJECT_VIEW,
        ToolWindowId.COMMIT,
        AGENT_SESSIONS_TOOL_WINDOW_ID,
        AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID,
        ToolWindowId.STRUCTURE_VIEW,
      )
    val structureView = builder.left.windows.last()
    assertThat(structureView.isVisible).isTrue()
    assertThat(structureView.weight).isEqualTo(0.4f)
    assertThat(structureView.contentUiType).isEqualTo(ToolWindowDescriptor.ToolWindowContentUiType.COMBO)
    assertThat(structureView.isSplit).isTrue()
    assertThat(structureView.sideWeight).isEqualTo(0.7f)
  }

  @Test
  fun factoryDefaultLayoutPlacesAgentToolWindowsBeforeStructure() {
    val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = true)

    layoutManager.noStateLoaded()

    val leftSplitToolWindowIds = layoutManager.getLayoutCopy().getInfos().values
      .filter { it.anchor == ToolWindowAnchor.LEFT && it.isSplit }
      .sortedBy { it.order }
      .map { it.id }
    assertThat(leftSplitToolWindowIds)
      .containsSubsequence(
        AGENT_SESSIONS_TOOL_WINDOW_ID,
        AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID,
        ToolWindowId.STRUCTURE_VIEW,
      )
  }
}

private class RecordingDefaultToolWindowLayoutBuilder : DefaultToolWindowLayoutBuilder {
  private val windowBuilders = LinkedHashMap<String, RecordingDefaultToolWindowDescriptorBuilder>()
  override val left: RecordingDefaultToolWindowStripeBuilder = RecordingDefaultToolWindowStripeBuilder(
    ToolWindowDescriptor.ToolWindowAnchor.LEFT,
    windowBuilders,
  )
  override val right: RecordingDefaultToolWindowStripeBuilder = RecordingDefaultToolWindowStripeBuilder(
    ToolWindowDescriptor.ToolWindowAnchor.RIGHT,
    windowBuilders,
  )
  override val bottom: RecordingDefaultToolWindowStripeBuilder = RecordingDefaultToolWindowStripeBuilder(
    ToolWindowDescriptor.ToolWindowAnchor.BOTTOM,
    windowBuilders,
  )

  override fun removeAll(predicate: ((DefaultToolWindowDescriptorBuilder) -> Boolean)?) {
    if (predicate == null) {
      windowBuilders.clear()
    }
    else {
      windowBuilders.values.removeAll(predicate)
    }
  }
}

private class RecordingDefaultToolWindowStripeBuilder(
  private val stripeAnchor: ToolWindowDescriptor.ToolWindowAnchor,
  private val windowBuilders: LinkedHashMap<String, RecordingDefaultToolWindowDescriptorBuilder>,
) : DefaultToolWindowStripeBuilder {
  val windows: List<RecordingDefaultToolWindowDescriptorBuilder>
    get() = windowBuilders.values.filter { it.anchor == stripeAnchor }

  override fun addOrUpdate(id: String, buildToolWindow: (DefaultToolWindowDescriptorBuilder.() -> Unit)?) {
    val builder = windowBuilders.getOrPut(id) { RecordingDefaultToolWindowDescriptorBuilder(id) }
    builder.anchor = stripeAnchor
    buildToolWindow?.invoke(builder)
  }

  override fun addPlatformDefaultsV1() {
  }

  override fun addPlatformDefaultsV2() {
  }
}

private class RecordingDefaultToolWindowDescriptorBuilder(
  override val id: String,
) : DefaultToolWindowDescriptorBuilder {
  override var anchor: ToolWindowDescriptor.ToolWindowAnchor = ToolWindowDescriptor.ToolWindowAnchor.LEFT
  override var isVisible: Boolean = false
  override var weight: Float = 0.33f
  override var contentUiType: ToolWindowDescriptor.ToolWindowContentUiType = ToolWindowDescriptor.ToolWindowContentUiType.TABBED
  override var isSplit: Boolean = false
  override var sideWeight: Float = 0.5f
}

private const val AGENT_SESSIONS_TOOL_WINDOW_ID: String = "agent.workbench.sessions"
