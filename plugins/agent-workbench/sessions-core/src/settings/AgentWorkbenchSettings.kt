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
    get() = state.colorTabsBySourceProject

  fun setColorTabsBySourceProject(enabled: Boolean) {
    if (state.colorTabsBySourceProject == enabled) return
    updateState { current -> current.copy(colorTabsBySourceProject = enabled) }
    ApplicationManager.getApplication().messageBus.syncPublisher(AgentWorkbenchSettingsListener.TOPIC).colorTabsBySourceProjectChanged()
  }

  @Serializable
  data class SettingsState(
    @JvmField val colorTabsBySourceProject: Boolean = true,
  )

  companion object {
    fun getInstance(): AgentWorkbenchSettings = service()
  }
}
