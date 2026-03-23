// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.CommonBundle
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSelectionMode
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSources
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensions
import com.intellij.agent.workbench.prompt.ui.AgentPromptContextRemovalDecisions.removeManualContextItemsAfterExplicitRemoval
import com.intellij.agent.workbench.prompt.ui.AgentPromptContextRemovalDecisions.resolveContextEntriesAfterRemoval
import com.intellij.agent.workbench.prompt.ui.context.AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptImagePasteHandler
import com.intellij.agent.workbench.prompt.ui.context.IMAGE_PASTE_SOURCE_ID
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorTextField
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

private const val CONTEXT_SOFT_CAP_CHARS = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS

internal class AgentPromptPaletteContextController(
  private val project: Project,
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: EditorTextField,
  private val view: AgentPromptPaletteView,
  private val contextResolverService: AgentPromptContextResolverService,
  private val contextChips: AgentPromptContextChipsComponent,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val state: AgentPromptPaletteContextState,
  private val resolveWorkingProjectPath: () -> String?,
  private val resolveContextProjectBasePath: () -> String?,
  private val showError: (@Nls String) -> Unit,
  private val onContextChanged: (@Nls String) -> Unit,
  private val onExtensionTabRemoved: (String) -> Unit,
  private val setTargetMode: (PromptTargetMode) -> Unit,
) {
  fun configureAddContextButton() {
    val availability = resolveAvailableManualContext()
    if (availability != null && availability.sources.isNotEmpty()) {
      view.addContextButton.addActionListener {
        showManualContextSourceChooser(anchorComponent = view.addContextButton)
      }
    }
    else {
      view.addContextButton.isVisible = false
    }
  }

  fun loadInitialContext() {
    val resolved = contextResolverService.collectDefaultContext(invocationData)
    state.initialAutoContextFingerprint = computeContextFingerprint(resolved)
    state.removedAutoLogicalItemIds.clear()
    state.autoContextEntries = resolved.map { item ->
      ContextEntry(
        item = item,
        origin = ContextEntryOrigin.AUTO,
      )
    }
    state.manualContextItemsBySourceId.clear()
    refreshContextEntries()
  }

  fun refreshContextEntries() {
    state.contextEntries = buildVisibleContextEntries()
    contextChips.render(state.contextEntries)
  }

  fun buildVisibleContextEntries(): List<ContextEntry> {
    return materializeVisibleContextEntries(
      autoEntries = state.autoContextEntries,
      manualItemsBySourceId = state.manualContextItemsBySourceId,
      projectPath = resolveContextProjectBasePath(),
    )
  }

  fun resolveExtensionTabs() {
    val items = state.contextEntries.map { it.item }
    val matchingExtensions = AgentPromptPaletteExtensions.allExtensions().filter { it.matches(items) }

    val toRemove = state.activeExtensionTabs.filter { entry -> matchingExtensions.none { it === entry.extension } }
    toRemove.forEach(::removeExtensionTab)

    val existingExtensions = state.activeExtensionTabs.map { it.extension }.toSet()
    matchingExtensions.filter { it !in existingExtensions }.forEach(::addExtensionTab)

    syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
  }

  fun syncActiveExtensionTab(selectedPanel: JPanel?) {
    state.activeExtensionTab = state.activeExtensionTabs.firstOrNull { it.tabPanel === selectedPanel }
  }

  fun selectAutoSelectExtensionTab() {
    val items = state.contextEntries.map { it.item }
    val target = state.activeExtensionTabs.firstOrNull { it.extension.shouldAutoSelect(items) } ?: return
    val index = (0 until view.tabbedPane.tabCount).firstOrNull { view.tabbedPane.getComponentAt(it) === target.tabPanel }
    if (index != null) {
      view.tabbedPane.selectedIndex = index
    }
  }

  fun removeContextEntry(entry: ContextEntry) {
    if (entry.origin == ContextEntryOrigin.MANUAL) {
      val updatedManualItems = removeManualContextItemsAfterExplicitRemoval(
        manualItemsBySourceId = state.manualContextItemsBySourceId,
        removedEntry = entry,
        projectPath = resolveContextProjectBasePath(),
      )
      if (updatedManualItems == state.manualContextItemsBySourceId) {
        return
      }
      state.manualContextItemsBySourceId.clear()
      state.manualContextItemsBySourceId.putAll(updatedManualItems)
    }
    else {
      val beforeEntries = state.autoContextEntries
      val updatedEntries = resolveContextEntriesAfterRemoval(beforeEntries, entry.id)
      state.removedAutoLogicalItemIds.addAll(
        collectRemovedLogicalItemIds(
          beforeEntries = beforeEntries,
          afterEntries = updatedEntries,
        )
      )
      state.autoContextEntries = updatedEntries
    }
    refreshContextEntries()
    resolveExtensionTabs()
    onContextChanged(AgentPromptBundle.message("popup.status.context.removed"))
  }

  fun installImagePasteHandler() {
    promptArea.addSettingsProvider { editor ->
      editor.putUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY, AgentPromptImagePasteHandler { item ->
        val existing = state.manualContextItemsBySourceId[IMAGE_PASTE_SOURCE_ID].orEmpty()
        state.manualContextItemsBySourceId[IMAGE_PASTE_SOURCE_ID] = existing + item
        refreshContextEntries()
        resolveExtensionTabs()
        onContextChanged(AgentPromptBundle.message("popup.status.context.added"))
      })
    }
  }

  fun resolveContextSelection(items: List<AgentPromptContextItem>, projectPath: String?): AgentPromptPaletteContextSelection? {
    val baseSummary = AgentPromptContextEnvelopeSummary(
      softCapChars = CONTEXT_SOFT_CAP_CHARS,
      softCapExceeded = false,
      autoTrimApplied = false,
    )
    if (items.isEmpty()) {
      return AgentPromptPaletteContextSelection(items = emptyList(), summary = baseSummary)
    }

    val normalizedItems = items.map { item -> item.copy(body = item.body.trim()) }
    val serializedChars = AgentPromptContextEnvelopeFormatter.measureContextBlockChars(
      items = normalizedItems,
      summary = baseSummary,
      projectPath = projectPath,
    )
    if (serializedChars <= CONTEXT_SOFT_CAP_CHARS) {
      return AgentPromptPaletteContextSelection(items = normalizedItems, summary = baseSummary)
    }

    val choice = Messages.showDialog(
      project,
      AgentPromptBundle.message("popup.context.softcap.message", serializedChars, CONTEXT_SOFT_CAP_CHARS),
      AgentPromptBundle.message("popup.context.softcap.title"),
      arrayOf(
        AgentPromptBundle.message("popup.context.softcap.action.send.full"),
        AgentPromptBundle.message("popup.context.softcap.action.auto.trim"),
        CommonBundle.getCancelButtonText(),
      ),
      0,
      Messages.getWarningIcon(),
    )

    return when (choice) {
      0 -> AgentPromptPaletteContextSelection(
        items = normalizedItems,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = CONTEXT_SOFT_CAP_CHARS,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )

      1 -> {
        val trimResult = AgentPromptContextEnvelopeFormatter.applySoftCap(
          items = normalizedItems,
          softCapChars = CONTEXT_SOFT_CAP_CHARS,
          projectPath = projectPath,
        )
        AgentPromptPaletteContextSelection(
          items = trimResult.items,
          summary = AgentPromptContextEnvelopeSummary(
            softCapChars = CONTEXT_SOFT_CAP_CHARS,
            softCapExceeded = true,
            autoTrimApplied = true,
          ),
        )
      }

      else -> null
    }
  }

  private fun resolveAvailableManualContext(): ManualContextAvailability? {
    return resolveManualContextAvailability(
      hostProject = project,
      invocationData = invocationData,
      launcher = launcherProvider(),
      sources = AgentPromptManualContextSources.allSources(),
    )
  }

  private fun showManualContextSourceChooser(anchorComponent: JComponent) {
    val availability = resolveAvailableManualContext() ?: return
    val sourceProject = availability.sourceProject
    val sources = availability.sources
    if (sources.isEmpty()) {
      return
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(sources)
      .setRenderer(object : ColoredListCellRenderer<AgentPromptManualContextSourceBridge>() {
        override fun customizeCellRenderer(
          list: JList<out AgentPromptManualContextSourceBridge>,
          value: AgentPromptManualContextSourceBridge?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          value ?: return
          append(value.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      })
      .setItemChosenCallback { source ->
        source.showPicker(
          AgentPromptManualContextPickerRequest(
            hostProject = project,
            sourceProject = sourceProject,
            invocationData = invocationData,
            workingProjectPath = resolveWorkingProjectPath(),
            currentItems = state.manualContextItemsBySourceId[source.sourceId].orEmpty(),
            anchorComponent = anchorComponent,
            onSelected = { item -> applyManualContextSelection(source = source, item = item) },
            onError = showError,
          )
        )
      }
      .createPopup()
      .showUnderneathOf(anchorComponent)
  }

  private fun applyManualContextSelection(
    source: AgentPromptManualContextSourceBridge,
    item: AgentPromptContextItem,
  ) {
    val updatedItems = when (source.selectionMode) {
      AgentPromptManualContextSelectionMode.REPLACE -> listOf(item)
      AgentPromptManualContextSelectionMode.APPEND -> state.manualContextItemsBySourceId[source.sourceId].orEmpty() + item
    }
    state.manualContextItemsBySourceId[source.sourceId] = updatedItems
    refreshContextEntries()
    resolveExtensionTabs()
    onContextChanged(AgentPromptBundle.message("popup.status.context.added"))
  }

  private fun addExtensionTab(extension: AgentPromptPaletteExtension) {
    val panel = JPanel()
    val key = "extension:" + extension.javaClass.name
    view.tabbedPane.addTab(extension.getTabTitle(), panel)
    state.activeExtensionTabs = state.activeExtensionTabs.plus(
      AgentPromptPaletteExtensionTab(
        extension = extension,
        tabPanel = panel,
        taskKey = key,
      )
    )
  }

  private fun removeExtensionTab(entry: AgentPromptPaletteExtensionTab) {
    val index = (0 until view.tabbedPane.tabCount).firstOrNull { view.tabbedPane.getComponentAt(it) === entry.tabPanel }
    if (index != null) {
      view.tabbedPane.removeTabAt(index)
    }
    state.activeExtensionTabs = state.activeExtensionTabs.filter { it !== entry }
    onExtensionTabRemoved(entry.taskKey)
    if (state.activeExtensionTab === entry) {
      state.activeExtensionTab = null
      setTargetMode(PromptTargetMode.NEW_TASK)
    }
  }
}

internal data class AgentPromptPaletteContextSelection(
  @JvmField val items: List<AgentPromptContextItem>,
  @JvmField val summary: AgentPromptContextEnvelopeSummary,
)
