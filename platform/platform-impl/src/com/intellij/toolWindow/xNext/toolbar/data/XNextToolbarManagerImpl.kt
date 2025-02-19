// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.data

import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jdom.Element

internal class XNextToolbarManagerImpl(val project: Project, val scope: CoroutineScope) : XNextToolbarManager {
  companion object {
    private const val MAX_RECENT_COUNT = 3
    // private const val MAX_PINNED_COUNT = 5

    private val actEvent = listOf(
      ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow,
      ToolWindowManagerListener.ToolWindowManagerEventType.ShowToolWindow,
    )

    private val regEvent = listOf(
      ToolWindowManagerListener.ToolWindowManagerEventType.UnregisterToolWindow,
      ToolWindowManagerListener.ToolWindowManagerEventType.ToolWindowAvailable,
      ToolWindowManagerListener.ToolWindowManagerEventType.ToolWindowUnavailable,
      ToolWindowManagerListener.ToolWindowManagerEventType.RegisterToolWindow)
  }

  private val toolWindowManager = ToolWindowManager.getInstance(project) as ToolWindowManagerImpl

  private var pinnedList = listOf<String>()

  override val xNextToolbarState: XNextToolbarState
    get() = currentState


  private var currentState: XNextToolbarState = XNextToolbarState.EMPTY
  private var flow: MutableStateFlow<XNextToolbarState> = MutableStateFlow(currentState)

  private val pinnedService get() = ApplicationManager.getApplication().service<XNextPinnedService>()
  //override var xNextFlow: StateFlow<XNextToolbarState> = flow

  init {
    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager, changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
        if (toolWindowManager !is ToolWindowManagerImpl) return
        if (regEvent.contains(changeType)) {
          updatePinned()
        }
        else if (actEvent.contains(changeType)) {
          updateState()
        }
      }
    })
    updatePinned()
  }

  fun updatePinned() {
    val toolWindowIds = ToolWindowsGroup.getToolWindowActions(project, false).map { it.toolWindowId }

    pinnedList = pinnedService.getListOfPinnedIds().filter { toolWindowIds.contains(it) }
    updateState()
  }

  private fun updateState() {

    val toolWindowIds = ToolWindowsGroup.getToolWindowActions(project, false).map { it.toolWindowId }
    val allRecentIds = toolWindowManager.getRecentToolWindows().filter { toolWindowIds.contains(it) }
    val pinned = pinnedList.filter { toolWindowIds.contains(it) }

  //  val pinned = allPinned.take(MAX_PINNED_COUNT)
    var recent = allRecentIds.filter { !pinned.contains(it) }.take(MAX_RECENT_COUNT)

    val prevRecent = getCurrentState().recent
    if(prevRecent.toSet() == recent.toSet()) {
      recent = prevRecent
    }

    setState(XNextToolbarState(recent, pinned))
  }

  private fun setState(state: XNextToolbarState) {
    currentState = state
    flow.tryEmit(currentState)
  }

  private fun getCurrentState(): XNextToolbarState {
    //return flow.value
    return currentState
  }

  override fun updatePinned(linkSet: LinkedHashSet<String>) {
    pinnedService.updatePinned(linkSet)
    updatePinned()
  }

  override fun setPinned(id: String, pinned: Boolean) {
    pinnedService.setPinned(id, pinned)
    updatePinned()
  }
}

@Service
@State(name = "XNextPinnedState", storages = [Storage("window.state.xml", roamingType = RoamingType.DISABLED)])
private class XNextPinnedService : PersistentStateComponent<Element> {

  private val pinnedIds = linkedSetOf("Project", "Commit", "Bookmarks")

  fun getListOfPinnedIds(): List<String> = pinnedIds.toList()
  fun updatePinned(linkSet: LinkedHashSet<String>) {
    pinnedIds.clear()
    pinnedIds.addAll(linkSet)
  }

  fun setPinned(id: String, pinned: Boolean) {
    if (pinned) {
      pinnedIds += id
    }
    else {
      pinnedIds -= id
    }
  }

  override fun getState(): Element = Element("pinnedIds").apply {
    for (id in pinnedIds) {
      addContent(Element("id").apply {
        setText(id)
      })
    }
  }

  override fun loadState(state: Element) {
    pinnedIds.clear()
    for (child in state.children) {
      if (child.name == "id") {
        pinnedIds.add(child.text)
      }
    }
  }
}