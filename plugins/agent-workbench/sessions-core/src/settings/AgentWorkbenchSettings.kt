// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import kotlinx.serialization.Serializable

interface AgentWorkbenchSettingsListener {
  fun colorTabsBySourceProjectChanged()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<AgentWorkbenchSettingsListener> = Topic.create(
      "Agent Workbench Settings",
      AgentWorkbenchSettingsListener::class.java,
    )
  }
}

@Service(Service.Level.APP)
@State(name = "AgentWorkbenchSettings", storages = [Storage("agentWorkbenchSettings.xml")])
class AgentWorkbenchSettings : SerializablePersistentStateComponent<AgentWorkbenchSettings.SettingsState>(SettingsState()) {
  val colorTabsBySourceProject: Boolean
    get() = state.colorTabsBySourceProject ?: COLOR_TABS_BY_SOURCE_PROJECT_DEFAULT

  val colorTabsBySourceProjectOverride: Boolean?
    get() = state.colorTabsBySourceProject

  val openInDedicatedFrame: Boolean
    get() = state.openInDedicatedFrame ?: OPEN_IN_DEDICATED_FRAME_DEFAULT

  val openInDedicatedFrameOverride: Boolean?
    get() = state.openInDedicatedFrame

  val showAgentActivityInMainToolbar: Boolean
    get() = state.showAgentActivityInMainToolbar ?: SHOW_AGENT_ACTIVITY_IN_MAIN_TOOLBAR_DEFAULT

  val showAgentActivityInMainToolbarOverride: Boolean?
    get() = state.showAgentActivityInMainToolbar

  fun setColorTabsBySourceProject(enabled: Boolean) {
    val previousValue = colorTabsBySourceProject
    val storedValue = nonDefaultBooleanValue(enabled, COLOR_TABS_BY_SOURCE_PROJECT_DEFAULT)
    if (state.colorTabsBySourceProject == storedValue) return
    val updatedState = updateState { current -> current.copy(colorTabsBySourceProject = storedValue) }
    val updatedValue = updatedState.colorTabsBySourceProject ?: COLOR_TABS_BY_SOURCE_PROJECT_DEFAULT
    if (previousValue != updatedValue) {
      ApplicationManager.getApplication().messageBus.syncPublisher(AgentWorkbenchSettingsListener.TOPIC).colorTabsBySourceProjectChanged()
    }
  }

  fun migrateOpenInDedicatedFrame(enabled: Boolean): Boolean {
    state.openInDedicatedFrame?.let { return it }
    val storedValue = nonDefaultBooleanValue(enabled, OPEN_IN_DEDICATED_FRAME_DEFAULT)
    if (storedValue == null) return openInDedicatedFrame
    val updatedState = updateState { current ->
      if (current.openInDedicatedFrame != null) current else current.copy(openInDedicatedFrame = storedValue)
    }
    return updatedState.openInDedicatedFrame ?: OPEN_IN_DEDICATED_FRAME_DEFAULT
  }

  fun setOpenInDedicatedFrame(enabled: Boolean) {
    val storedValue = nonDefaultBooleanValue(enabled, OPEN_IN_DEDICATED_FRAME_DEFAULT)
    if (state.openInDedicatedFrame == storedValue) return
    updateState { current -> current.copy(openInDedicatedFrame = storedValue) }
  }

  fun setShowAgentActivityInMainToolbar(enabled: Boolean) {
    val storedValue = nonDefaultBooleanValue(enabled, SHOW_AGENT_ACTIVITY_IN_MAIN_TOOLBAR_DEFAULT)
    if (state.showAgentActivityInMainToolbar == storedValue) return
    updateState { current -> current.copy(showAgentActivityInMainToolbar = storedValue) }
  }

  override fun loadState(state: SettingsState) {
    super.loadState(state.normalized())
  }

  @Serializable
  data class SettingsState(
    @JvmField val colorTabsBySourceProject: Boolean? = null,
    @JvmField val openInDedicatedFrame: Boolean? = null,
    @JvmField val showAgentActivityInMainToolbar: Boolean? = null,
  ) {
    fun normalized(): SettingsState {
      return copy(
        colorTabsBySourceProject = nonDefaultBooleanValue(colorTabsBySourceProject, COLOR_TABS_BY_SOURCE_PROJECT_DEFAULT),
        openInDedicatedFrame = nonDefaultBooleanValue(openInDedicatedFrame, OPEN_IN_DEDICATED_FRAME_DEFAULT),
        showAgentActivityInMainToolbar = nonDefaultBooleanValue(
          showAgentActivityInMainToolbar,
          SHOW_AGENT_ACTIVITY_IN_MAIN_TOOLBAR_DEFAULT,
        ),
      )
    }
  }

  companion object {
    fun getInstance(): AgentWorkbenchSettings = service()
  }
}

private const val COLOR_TABS_BY_SOURCE_PROJECT_DEFAULT: Boolean = true
private const val OPEN_IN_DEDICATED_FRAME_DEFAULT: Boolean = true
private const val SHOW_AGENT_ACTIVITY_IN_MAIN_TOOLBAR_DEFAULT: Boolean = false

private fun nonDefaultBooleanValue(value: Boolean?, defaultValue: Boolean): Boolean? {
  return value?.takeIf { it != defaultValue }
}
