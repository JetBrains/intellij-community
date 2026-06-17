// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.icons.AllIcons
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionOutlineItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionOutlineItemKind
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadOutline
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
import kotlinx.coroutines.channels.Channel
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

  init {
    startActiveThreadUpdateSubscription()
  }

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

  private fun startActiveThreadUpdateSubscription() {
    val provider = file.provider ?: return
    val source = AgentSessionProviders.find(provider)?.sessionSource ?: return
    val projectPath = file.projectPath.takeIf(String::isNotBlank) ?: return
    val threadId = file.threadId.takeIf(String::isNotBlank) ?: return
    cs.launch {
      source.activeThreadUpdateEvents(path = projectPath, threadId = threadId).collect {
        root.requestReload()
      }
    }
  }
}

private class AgentChatLazyOutlineElement(
  private val file: AgentChatVirtualFile,
  private val cs: CoroutineScope,
  private val notifyModelChanged: () -> Unit,
) : StructureViewTreeElement {
  private val loadStarted = AtomicBoolean(false)
  private val reloadRequests = Channel<Unit>(Channel.CONFLATED)

  @Volatile
  private var delegate: AgentChatStructureViewElement = loadingElement()

  @Volatile
  private var currentFingerprint: AgentChatOutlineFingerprint? = null

  override fun getValue(): Any = this

  override fun getPresentation(): ItemPresentation = delegate.presentation

  override fun getChildren(): Array<StructureViewTreeElement> {
    startLoading()
    return delegate.children
  }

  override fun navigate(requestFocus: Boolean) = Unit

  override fun canNavigate(): Boolean = false

  override fun canNavigateToSource(): Boolean = false

  fun requestReload() {
    if (!loadStarted.get()) {
      return
    }
    reloadRequests.trySend(Unit)
  }

  private fun startLoading() {
    if (!loadStarted.compareAndSet(false, true)) {
      return
    }
    cs.launch {
      while (true) {
        val loaded = loadElement()
        withContext(Dispatchers.EDT) {
          if (currentFingerprint != loaded.fingerprint) {
            currentFingerprint = loaded.fingerprint
            delegate = loaded.element
            notifyModelChanged()
          }
        }
        reloadRequests.receive()
      }
    }
  }

  private suspend fun loadElement(): AgentChatOutlineLoadResult {
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
        fingerprint = outline.toStructureViewFingerprint(),
      )
    }
    return AgentChatOutlineLoadResult(
      element = AgentChatStructureViewElement.root(
        title = outline.title,
        icon = providerIcon(provider = outline.provider),
        children = outline.items.mapIndexed { index, item ->
          item.toStructureViewElement(
            file = file,
            source = source,
            cs = cs,
            autoExpand = outline.items.size == 1 && index == 0,
          )
        },
      ),
      fingerprint = outline.toStructureViewFingerprint(),
    )
  }

  private fun loadingElement(): AgentChatStructureViewElement {
    return statusRoot(
      title = file.threadTitle,
      status = AgentChatBundle.message("chat.structure.loading"),
      icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
    ).element
  }

  private fun unavailableElement(): AgentChatOutlineLoadResult {
    val status = AgentChatBundle.message("chat.structure.unavailable")
    return statusRoot(
      title = file.threadTitle,
      status = status,
      icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
      fingerprint = AgentChatOutlineFingerprint.status(
        provider = file.provider?.value,
        threadId = file.threadId,
        title = file.threadTitle,
        status = status,
      ),
    )
  }

  private fun statusRoot(
    title: String,
    status: String,
    icon: Icon?,
    fingerprint: AgentChatOutlineFingerprint = AgentChatOutlineFingerprint.status(
      provider = file.provider?.value,
      threadId = file.threadId,
      title = title,
      status = status,
    ),
  ): AgentChatOutlineLoadResult {
    return AgentChatOutlineLoadResult(
      element = AgentChatStructureViewElement.root(
        title = title,
        icon = icon,
        children = listOf(
          AgentChatStructureViewElement.status(
            title = title,
            status = status,
            icon = icon,
          )
        ),
      ),
      fingerprint = fingerprint,
    )
  }
}

private data class AgentChatOutlineLoadResult(
  val element: AgentChatStructureViewElement,
  val fingerprint: AgentChatOutlineFingerprint,
)

private data class AgentChatOutlineFingerprint(
  val provider: String?,
  val threadId: String,
  val title: String,
  val updatedAt: Long?,
  val status: String?,
  val items: List<AgentChatOutlineItemFingerprint>,
) {
  companion object {
    fun status(provider: String?, threadId: String, title: String, status: String): AgentChatOutlineFingerprint {
      return AgentChatOutlineFingerprint(
        provider = provider,
        threadId = threadId,
        title = title,
        updatedAt = null,
        status = status,
        items = emptyList(),
      )
    }
  }
}

private data class AgentChatOutlineItemFingerprint(
  val id: String,
  val kind: AgentSessionOutlineItemKind,
  val title: String,
  val preview: String?,
  val timestampMs: Long?,
  val childCount: Int,
  val children: List<AgentChatOutlineItemFingerprint>,
)

private fun AgentSessionThreadOutline.toStructureViewFingerprint(): AgentChatOutlineFingerprint {
  return AgentChatOutlineFingerprint(
    provider = provider.value,
    threadId = threadId,
    title = title,
    updatedAt = updatedAt,
    status = null,
    items = items.map(AgentSessionOutlineItem::toStructureViewFingerprint),
  )
}

private fun AgentSessionOutlineItem.toStructureViewFingerprint(): AgentChatOutlineItemFingerprint {
  return AgentChatOutlineItemFingerprint(
    id = id,
    kind = kind,
    title = title,
    preview = preview,
    timestampMs = timestampMs,
    childCount = children.size,
    children = children.map(AgentSessionOutlineItem::toStructureViewFingerprint),
  )
}

internal data class AgentChatStructureViewOutlineTarget(
  @JvmField val file: AgentChatVirtualFile,
  @JvmField val source: AgentSessionSource,
  @JvmField val item: AgentSessionOutlineItem,
)

internal interface AgentChatStructureViewOutlineElement {
  val outlineTarget: AgentChatStructureViewOutlineTarget?
}

internal class AgentChatStructureViewElement(
  private val value: Any,
  private val title: @NlsSafe String,
  private val location: @NlsSafe String?,
  private val tooltip: @NlsSafe String? = null,
  private val icon: Icon? = null,
  private val childrenElements: List<StructureViewTreeElement> = emptyList(),
  val autoExpand: Boolean = false,
  private val cs: CoroutineScope? = null,
  override val outlineTarget: AgentChatStructureViewOutlineTarget? = null,
) : StructureViewTreeElement, AgentChatStructureViewOutlineElement {
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

  override fun navigate(requestFocus: Boolean) {
    val target = outlineTarget ?: return
    val scope = cs ?: return
    if (!canNavigate()) {
      return
    }
    scope.launch {
      target.source.navigateThreadOutlineItem(
        path = target.file.projectPath,
        threadId = target.file.threadId,
        itemId = target.item.id,
        subAgentId = target.file.subAgentId,
        tabKey = target.file.tabKey,
      )
    }
  }

  override fun canNavigate(): Boolean {
    val target = outlineTarget ?: return false
    return target.source.canNavigateThreadOutlineItem(
      path = target.file.projectPath,
      threadId = target.file.threadId,
      itemId = target.item.id,
      subAgentId = target.file.subAgentId,
      tabKey = target.file.tabKey,
    )
  }

  override fun canNavigateToSource(): Boolean = canNavigate()

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

private fun AgentSessionOutlineItem.toStructureViewElement(
  file: AgentChatVirtualFile,
  source: AgentSessionSource,
  cs: CoroutineScope,
  autoExpand: Boolean = false,
): AgentChatStructureViewElement {
  val location = compactStructureViewLocation(preview ?: timestampMs?.let(::formatStructureViewTimestamp))
  return AgentChatStructureViewElement(
    value = this,
    title = title.takeIf { it.isNotBlank() } ?: kind.localizedTitle(),
    location = location,
    tooltip = preview ?: timestampMs?.let(::formatStructureViewTimestamp),
    icon = structureViewIcon(),
    autoExpand = autoExpand,
    cs = cs,
    outlineTarget = AgentChatStructureViewOutlineTarget(file = file, source = source, item = this),
    childrenElements = children.map { child ->
      child.toStructureViewElement(file = file, source = source, cs = cs)
    },
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
