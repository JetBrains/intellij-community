// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
  private val liveTerminalRegistry: AgentChatLiveTerminalRegistry = project.service<AgentChatLiveTerminalRegistryService>(),
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter = ApplicationAgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
) : UserDataHolderBase(), FileEditor {
  private val providerBehavior = resolveAgentChatProviderBehavior(file.provider)
  private val component = AgentChatFileEditorComponent {
    semanticRegionController?.occurrenceNavigator() ?: OccurenceNavigator.EMPTY
  }
  private val editorTabActions: ActionGroup? by lazy {
    val actionManager = ActionManager.getInstance()
    val providerActionIds = providerDescriptor?.editorTabActionIds.orEmpty()
    val actions = buildList {
      listOf(
        NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID,
        NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID,
        PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
        NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
      ).forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
      providerActionIds.forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
    }
    buildAgentChatEditorTabActionGroup(actions)
  }
  private val pendingThreadRefreshController = AgentChatPendingThreadRefreshController(
    file = file,
    behavior = providerBehavior,
    tabSnapshotWriter = tabSnapshotWriter,
    currentTimeProvider = currentTimeProvider,
    retryIntervalMs = pendingScopedRefreshRetryIntervalMs,
  )
  private val concreteThreadRebindController = AgentChatConcreteThreadRebindController(
    file = file,
    behavior = providerBehavior,
    tabSnapshotWriter = tabSnapshotWriter,
    currentTimeProvider = currentTimeProvider,
  )
  private val initialMessageDispatcher = AgentChatInitialMessageDispatcher(
    file = file,
    behavior = providerBehavior,
    tabSnapshotWriter = tabSnapshotWriter,
  )

  private var tab: AgentChatTerminalTab? = null
  private var initializationStarted: Boolean = false
  private var disposed: Boolean = false
  private var patchFoldController: AgentChatDisposableController? = null
  private var semanticRegionController: AgentChatSemanticRegionController? = null

  private val providerDescriptor
    get() = file.provider?.let(AgentSessionProviders::find)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = file.threadTitle

  override fun getTabActions(): ActionGroup? = editorTabActions

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = !disposed

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): AgentChatVirtualFile = file

  override fun selectNotify() {
    ensureInitialized()
  }

  override fun dispose() {
    disposed = true
    initialMessageDispatcher.dispose()
    pendingThreadRefreshController.dispose()
    concreteThreadRebindController.dispose()
    patchFoldController?.dispose()
    patchFoldController = null
    semanticRegionController?.dispose()
    semanticRegionController = null
    tab = null
    component.removeAll()
  }

  private fun ensureInitialized() {
    if (initializationStarted || disposed) {
      return
    }
    initializationStarted = true
    try {
      val createdTab = liveTerminalRegistry.acquireOrCreate(file = file, terminalTabs = terminalTabs)
      tab = createdTab
      pendingThreadRefreshController.attach(createdTab)
      concreteThreadRebindController.attach(createdTab, providerDescriptor)
      initialMessageDispatcher.schedule(createdTab)
      patchFoldController = providerBehavior.createPatchFoldController(createdTab)
      semanticRegionController = providerBehavior.createSemanticRegionController(createdTab)
      component.removeAll()
      component.add(createdTab.component, BorderLayout.CENTER)
      component.revalidate()
      component.repaint()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
    }
  }

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    initialMessageDispatcher.schedule(initializedTab)
  }

  internal fun canNavigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.canNavigate(direction) == true
  }

  internal fun navigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.navigate(direction) == true
  }
}

private class AgentChatFileEditorComponent(
  private val navigatorProvider: () -> OccurenceNavigator,
) : JPanel(BorderLayout()), OccurenceNavigator {
  override fun hasNextOccurence(): Boolean = navigatorProvider().hasNextOccurence()

  override fun hasPreviousOccurence(): Boolean = navigatorProvider().hasPreviousOccurence()

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goNextOccurence()

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goPreviousOccurence()

  override fun getNextOccurenceActionName(): String = navigatorProvider().nextOccurenceActionName

  override fun getPreviousOccurenceActionName(): String = navigatorProvider().previousOccurenceActionName

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal fun interface AgentChatTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentChatTabSnapshot)
}

private object ApplicationAgentChatTabSnapshotWriter : AgentChatTabSnapshotWriter {
  override suspend fun upsert(snapshot: AgentChatTabSnapshot) {
    serviceAsync<AgentChatTabsService>().upsert(snapshot)
  }
}

internal fun buildAgentChatEditorTabActionGroup(actions: List<AnAction>): ActionGroup? {
  if (actions.isEmpty()) {
    return null
  }
  if (actions.size == 1) {
    val singleAction = actions.single()
    return singleAction as? ActionGroup ?: DumbAwareAgentChatActionGroup(singleAction)
  }
  return DumbAwareAgentChatActionGroup(actions)
}

private class DumbAwareAgentChatActionGroup : DefaultActionGroup, DumbAware {
  constructor(vararg actions: AnAction) : super(*actions)

  constructor(actions: List<AnAction>) : super(actions)
}

private const val NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_QUICK
private const val NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_POPUP
private const val PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String =
  AgentWorkbenchActionIds.Sessions.EditorTab.PREVIOUS_PROPOSED_PLAN
private const val NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEXT_PROPOSED_PLAN
