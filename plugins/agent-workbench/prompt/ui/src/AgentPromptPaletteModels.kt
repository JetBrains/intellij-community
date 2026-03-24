// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRenderers
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal data class ProviderEntry(
    @JvmField val bridge: AgentSessionProviderDescriptor,
    @JvmField val displayName: @Nls String,
    @JvmField val isCliAvailable: Boolean,
    @JvmField val icon: Icon,
)

internal data class ContextEntry(
  @JvmField val item: AgentPromptContextItem,
  @JvmField val projectBasePath: String? = null,
  @JvmField val id: String = item.rendererId + ":" + item.title + ":" + item.body.hashCode(),
  @JvmField val origin: ContextEntryOrigin = ContextEntryOrigin.AUTO,
  @JvmField val manualSourceId: String? = null,
  @JvmField val backingItem: AgentPromptContextItem = item,
) {
  val logicalItemId: String?
    get() = item.itemId

  val logicalParentItemId: String?
    get() = item.parentItemId

  private val chipRender = AgentPromptContextRenderers.find(item.rendererId)
    ?.renderChip(
      AgentPromptChipRenderInput(
        item = item,
        projectBasePath = projectBasePath,
      )
    )

  val displayText: String = chipRender?.text ?: buildDefaultDisplayText()

  private val fallbackTooltipText: String by lazy(LazyThreadSafetyMode.NONE) {
    AgentPromptContextEnvelopeFormatter.renderContextItem(item = item, projectPath = projectBasePath)
  }

  val tooltipText: String
    get() = chipRender?.tooltipText ?: fallbackTooltipText

  private fun buildDefaultDisplayText(): String {
    val title = item.title?.takeIf { it.isNotBlank() } ?: "Context"
    val firstLine = item.body.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.isEmpty()) {
      return title
    }
    val preview = if (firstLine.length <= 60) firstLine else firstLine.take(60) + "\u2026"
    return "$title: $preview"
  }
}

internal enum class ContextEntryOrigin {
  AUTO,
  MANUAL,
}

internal data class ThreadEntry(
  @JvmField val id: String,
  @JvmField val displayText: @NlsSafe String,
  @JvmField val secondaryText: @NlsSafe String,
  @JvmField val activity: AgentThreadActivity = AgentThreadActivity.READY,
)

internal data class ManualContextAvailability(
  @JvmField val sourceProject: Project,
  @JvmField val sources: List<AgentPromptManualContextSourceBridge>,
)

internal data class AgentPromptPaletteExtensionTab(
  @JvmField val extension: AgentPromptPaletteExtension,
  @JvmField val tabPanel: JPanel,
  @JvmField val taskKeyPrefix: String,
)

internal class AgentPromptPaletteContextState {
  var autoContextEntries: List<ContextEntry> = emptyList()
  var contextEntries: List<ContextEntry> = emptyList()
  var initialAutoContextFingerprint: HashValue128? = null
  val removedAutoLogicalItemIds = LinkedHashSet<String>()
  val manualContextItemsBySourceId: MutableMap<String, List<AgentPromptContextItem>> = LinkedHashMap()
  var activeExtensionTabs: List<AgentPromptPaletteExtensionTab> = emptyList()
  var activeExtensionTab: AgentPromptPaletteExtensionTab? = null
}

internal class AgentPromptPaletteDraftState {
  var activeTaskKey: String? = null
  val taskPromptStates: MutableMap<String, AgentPromptTaskDraftState> = HashMap()
  var existingTaskSearchQuery: String = ""
  var isProgrammaticPromptUpdate: Boolean = false
}

internal class AgentPromptPaletteLaunchState {
  var selectedWorkingProjectPath: String? = null
  var canSubmitNow: Boolean = false
  var clearDraftOnClose: Boolean = false
}

internal class ExistingTaskCellRenderer : ListCellRenderer<ThreadEntry> {
  override fun getListCellRendererComponent(
    list: JList<out ThreadEntry>,
    value: ThreadEntry?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val component = SimpleColoredComponent()
    if (value != null) {
      component.append(value.displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      component.append(value.secondaryText, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    component.border = JBUI.Borders.empty(3, 4)
    component.isOpaque = true
    component.background = if (isSelected) list.selectionBackground else list.background
    component.foreground = if (isSelected) list.selectionForeground else list.foreground
    return component
  }
}
