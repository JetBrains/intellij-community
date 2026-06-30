// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec plugins/ij-air/spec/sessions/agent-sessions.spec.md

import com.intellij.agent.workbench.thread.view.AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.toolWindow.DefaultToolWindowDescriptorBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutExtension
import com.intellij.toolWindow.DefaultToolWindowStripeBuilder
import com.intellij.toolWindow.ToolWindowDescriptor

// Tool window XML only defines registration defaults. This extension seeds the normal project-frame default layout,
// where order, weight, and the split placement with Thread Outline are intentional.
internal class AgentSessionsToolWindowDefaultLayoutExtension : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
    addAgentToolWindows(builder)
  }

  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    addAgentToolWindows(builder)
  }
}

private fun addAgentToolWindows(builder: DefaultToolWindowLayoutBuilder) {
  val structureViewLayout = removeStructureView(builder)
  addAgentToolWindows(builder.left)
  // Keep Structure below the agent pair while preserving any defaults set by the platform layout extension.
  structureViewLayout?.let { layout ->
    builder.left.addOrUpdate(ToolWindowId.STRUCTURE_VIEW) {
      layout.applyTo(this)
    }
  }
}

private fun removeStructureView(builder: DefaultToolWindowLayoutBuilder): ToolWindowLayoutSnapshot? {
  var structureViewLayout: ToolWindowLayoutSnapshot? = null
  builder.removeAll {
    if (it.id == ToolWindowId.STRUCTURE_VIEW && it.anchor == ToolWindowDescriptor.ToolWindowAnchor.LEFT) {
      structureViewLayout = ToolWindowLayoutSnapshot.from(it)
      true
    }
    else {
      false
    }
  }
  return structureViewLayout
}

private fun addAgentToolWindows(builder: DefaultToolWindowStripeBuilder) {
  builder.addOrUpdate(AGENT_SESSIONS_TOOL_WINDOW_ID) {
    weight = AGENT_TOOL_WINDOW_WEIGHT
    isSplit = true
  }
  builder.addOrUpdate(AGENT_THREAD_VIEW_THREAD_OUTLINE_TOOL_WINDOW_ID) {
    weight = AGENT_TOOL_WINDOW_WEIGHT
    isSplit = true
  }
}

private data class ToolWindowLayoutSnapshot(
  private val isVisible: Boolean,
  private val weight: Float,
  private val contentUiType: ToolWindowDescriptor.ToolWindowContentUiType,
  private val isSplit: Boolean,
  private val sideWeight: Float,
) {
  fun applyTo(builder: DefaultToolWindowDescriptorBuilder) {
    builder.isVisible = isVisible
    builder.weight = weight
    builder.contentUiType = contentUiType
    builder.isSplit = isSplit
    builder.sideWeight = sideWeight
  }

  companion object {
    fun from(builder: DefaultToolWindowDescriptorBuilder): ToolWindowLayoutSnapshot {
      return ToolWindowLayoutSnapshot(
        isVisible = builder.isVisible,
        weight = builder.weight,
        contentUiType = builder.contentUiType,
        isSplit = builder.isSplit,
        sideWeight = builder.sideWeight,
      )
    }
  }
}

private const val AGENT_SESSIONS_TOOL_WINDOW_ID: String = "agent.workbench.sessions"
private const val AGENT_TOOL_WINDOW_WEIGHT: Float = 0.25f
