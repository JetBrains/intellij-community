// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.icons.AllIcons
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionOutlineItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionOutlineItemKind
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.FileEditorPositionListener
import com.intellij.ide.structureView.ModelListener
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

internal fun createAgentChatStructureViewBuilder(file: AgentChatVirtualFile): TreeBasedStructureViewBuilder? {
  if (!file.canShowAgentChatStructureView()) {
    return null
  }
  return object : TreeBasedStructureViewBuilder() {
    override fun createStructureViewModel(editor: Editor?): StructureViewModel {
      return AgentChatStructureViewModel(file = file)
    }

    override fun isRootNodeShown(): Boolean = false
  }
}

private fun AgentChatVirtualFile.canShowAgentChatStructureView(): Boolean {
  return !isPendingThread && validateAgentChatFile(this) == null
}

private class AgentChatStructureViewModel(
  private val file: AgentChatVirtualFile,
) : StructureViewModel, StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider {
  @Suppress("RAW_SCOPE_CREATION")
  private val cs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val modelListeners = CopyOnWriteArraySet<ModelListener>()
  private val root = AgentChatLazyOutlineElement(
    file = file,
    cs = cs,
    notifyModelChanged = ::notifyModelChanged,
  )

  @Volatile
  private var rootLoaded: Boolean = false

  override fun getRoot(): StructureViewTreeElement = root

  override fun getCurrentEditorElement(): Any? = null

  override fun addEditorPositionListener(listener: FileEditorPositionListener) = Unit

  override fun removeEditorPositionListener(listener: FileEditorPositionListener) = Unit

  override fun addModelListener(modelListener: ModelListener) {
    modelListeners += modelListener
    if (rootLoaded) {
      cs.launch(Dispatchers.EDT) {
        if (modelListener in modelListeners) {
          modelListener.onModelChanged()
        }
      }
    }
  }

  override fun removeModelListener(modelListener: ModelListener) {
    modelListeners -= modelListener
  }

  override fun shouldEnterElement(element: Any?): Boolean = false

  override fun getGroupers(): Array<Grouper> = Grouper.EMPTY_ARRAY

  override fun getSorters(): Array<Sorter> = Sorter.EMPTY_ARRAY

  override fun getFilters(): Array<Filter> = Filter.EMPTY_ARRAY

  override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = element.children.isNotEmpty()

  override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = element.children.isEmpty()

  override fun isAutoExpand(element: StructureViewTreeElement): Boolean {
    return element === root || (element as? AgentChatStructureViewElement)?.autoExpand == true
  }

  override fun isSmartExpand(): Boolean = false

  override fun dispose() {
    cs.cancel()
    modelListeners.clear()
  }

  private fun notifyModelChanged() {
    rootLoaded = true
    modelListeners.forEach(ModelListener::onModelChanged)
  }
}

private class AgentChatLazyOutlineElement(
  private val file: AgentChatVirtualFile,
  private val cs: CoroutineScope,
  private val notifyModelChanged: () -> Unit,
) : StructureViewTreeElement {
  private val loadStarted = AtomicBoolean(false)

  @Volatile
  private var delegate: AgentChatStructureViewElement = loadingElement()

  override fun getValue(): Any = this

  override fun getPresentation(): ItemPresentation = delegate.presentation

  override fun getChildren(): Array<StructureViewTreeElement> {
    startLoading()
    return delegate.children
  }

  override fun navigate(requestFocus: Boolean) = Unit

  override fun canNavigate(): Boolean = false

  override fun canNavigateToSource(): Boolean = false

  private fun startLoading() {
    if (!loadStarted.compareAndSet(false, true)) {
      return
    }
    cs.launch {
      val loadedElement = loadElement()
      withContext(Dispatchers.EDT) {
        delegate = loadedElement
        notifyModelChanged()
      }
    }
  }

  private suspend fun loadElement(): AgentChatStructureViewElement {
    val provider = file.provider ?: return unavailableElement()
    val source = AgentSessionProviders.find(provider)?.sessionSource ?: return unavailableElement()
    val outline = try {
      source.loadThreadOutline(path = file.projectPath, threadId = file.threadId, subAgentId = file.subAgentId)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      null
    }
    if (outline == null) {
      return unavailableElement()
    }
    if (outline.items.isEmpty()) {
      return statusRoot(
        title = outline.title,
        status = AgentChatBundle.message("chat.structure.empty"),
        icon = providerIcon(provider = outline.provider),
      )
    }
    return AgentChatStructureViewElement.root(
      title = outline.title,
      icon = providerIcon(provider = outline.provider),
      children = outline.items.mapIndexed { index, item ->
        item.toStructureViewElement(autoExpand = outline.items.size == 1 && index == 0)
      },
    )
  }

  private fun loadingElement(): AgentChatStructureViewElement {
    return statusRoot(
      title = file.threadTitle,
      status = AgentChatBundle.message("chat.structure.loading"),
      icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
    )
  }

  private fun unavailableElement(): AgentChatStructureViewElement {
    return statusRoot(
      title = file.threadTitle,
      status = AgentChatBundle.message("chat.structure.unavailable"),
      icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
    )
  }

  private fun statusRoot(title: String, status: String, icon: Icon?): AgentChatStructureViewElement {
    return AgentChatStructureViewElement.root(
      title = title,
      icon = icon,
      children = listOf(
        AgentChatStructureViewElement.status(
          title = title,
          status = status,
          icon = icon,
        )
      ),
    )
  }
}

private class AgentChatStructureViewElement(
  private val value: Any,
  private val title: @NlsSafe String,
  private val location: @NlsSafe String?,
  private val tooltip: @NlsSafe String? = null,
  private val icon: Icon? = null,
  private val childrenElements: List<StructureViewTreeElement> = emptyList(),
  val autoExpand: Boolean = false,
) : StructureViewTreeElement {
  override fun getValue(): Any = value

  override fun getPresentation(): ItemPresentation {
    return PresentationData(title, location, icon, null).also { presentation ->
      presentation.tooltip = tooltip ?: location
      presentation.clearText()
      presentation.addText(title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      if (!location.isNullOrBlank()) {
        val coloredLocation: @NlsSafe String = "  $location"
        presentation.addText(coloredLocation, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  override fun getChildren(): Array<StructureViewTreeElement> = childrenElements.toTypedArray()

  // Persisted outline items do not reliably map to live terminal TUI positions: providers can clear, redraw, or trim them.
  override fun navigate(requestFocus: Boolean) = Unit

  override fun canNavigate(): Boolean = false

  override fun canNavigateToSource(): Boolean = false

  companion object {
    fun root(title: String, icon: Icon?, children: List<StructureViewTreeElement>): AgentChatStructureViewElement {
      return AgentChatStructureViewElement(
        value = title,
        title = title,
        location = null,
        icon = icon,
        childrenElements = children,
      )
    }

    fun status(title: String, status: String, icon: Icon?): AgentChatStructureViewElement {
      return AgentChatStructureViewElement(
        value = status,
        title = title,
        location = status,
        tooltip = status,
        icon = icon,
      )
    }
  }
}

private fun AgentSessionOutlineItem.toStructureViewElement(autoExpand: Boolean = false): AgentChatStructureViewElement {
  val location = compactStructureViewLocation(preview ?: timestampMs?.let(::formatStructureViewTimestamp))
  return AgentChatStructureViewElement(
    value = this,
    title = title.takeIf { it.isNotBlank() } ?: kind.localizedTitle(),
    location = location,
    tooltip = preview ?: timestampMs?.let(::formatStructureViewTimestamp),
    icon = structureViewIcon(),
    autoExpand = autoExpand,
    childrenElements = children.map { child -> child.toStructureViewElement() },
  )
}

private fun AgentSessionOutlineItem.structureViewIcon(): Icon {
  return when (kind) {
    AgentSessionOutlineItemKind.USER_PROMPT -> AllIcons.General.User
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AllIcons.General.BalloonInformation
    AgentSessionOutlineItemKind.AGENT_WORK -> AllIcons.Actions.Execute
    AgentSessionOutlineItemKind.TOOL_CALL -> AllIcons.Nodes.Console
    AgentSessionOutlineItemKind.TOOL_RESULT -> if (isSuccessfulToolResult()) AllIcons.General.InspectionsOK else AllIcons.General.Warning
    AgentSessionOutlineItemKind.PLAN -> AllIcons.Actions.Checked
    AgentSessionOutlineItemKind.APPROVAL_REQUEST -> AllIcons.General.Warning
    AgentSessionOutlineItemKind.INPUT_REQUEST -> AllIcons.General.QuestionDialog
    AgentSessionOutlineItemKind.SUMMARY,
    AgentSessionOutlineItemKind.METADATA,
      -> AllIcons.General.Information
  }
}

private fun AgentSessionOutlineItem.isSuccessfulToolResult(): Boolean {
  return title == "Exit 0" || !title.startsWith("Exit ")
}

private fun formatStructureViewTimestamp(timestamp: Long): String {
  return AgentChatBundle.message("chat.structure.timestamp", DateFormatUtil.formatPrettyDateTime(timestamp))
}

private fun compactStructureViewLocation(value: String?): String? {
  val location = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return if (location.length <= 96) location else location.take(93).trimEnd() + "..."
}

private fun AgentSessionOutlineItemKind.localizedTitle(): String {
  return when (this) {
    AgentSessionOutlineItemKind.USER_PROMPT -> AgentChatBundle.message("chat.structure.kind.user.prompt")
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE -> AgentChatBundle.message("chat.structure.kind.assistant.response")
    AgentSessionOutlineItemKind.AGENT_WORK -> AgentChatBundle.message("chat.structure.kind.agent.work")
    AgentSessionOutlineItemKind.TOOL_CALL -> AgentChatBundle.message("chat.structure.kind.tool.call")
    AgentSessionOutlineItemKind.TOOL_RESULT -> AgentChatBundle.message("chat.structure.kind.tool.result")
    AgentSessionOutlineItemKind.PLAN -> AgentChatBundle.message("chat.structure.kind.plan")
    AgentSessionOutlineItemKind.APPROVAL_REQUEST -> AgentChatBundle.message("chat.structure.kind.approval.request")
    AgentSessionOutlineItemKind.INPUT_REQUEST -> AgentChatBundle.message("chat.structure.kind.input.request")
    AgentSessionOutlineItemKind.SUMMARY -> AgentChatBundle.message("chat.structure.kind.summary")
    AgentSessionOutlineItemKind.METADATA -> AgentChatBundle.message("chat.structure.kind.metadata")
  }
}
