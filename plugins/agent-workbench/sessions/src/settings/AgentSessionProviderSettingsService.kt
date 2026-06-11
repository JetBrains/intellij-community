// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.messages.Topic
import kotlinx.serialization.Serializable

interface AgentSessionProviderSettingsListener {
  fun providerSettingsChanged()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<AgentSessionProviderSettingsListener> = Topic.create(
      "Agent Session Provider Settings",
      AgentSessionProviderSettingsListener::class.java,
    )
  }
}

@Service(Service.Level.APP)
@State(
  name = "AgentSessionProviderSettings",
  storages = [
    Storage("agentWorkbenchSettings.xml"),
    Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, deprecated = true),
  ],
)
class AgentSessionProviderSettingsService
  : SerializablePersistentStateComponent<AgentSessionProviderSettingsService.ProviderSettingsState>(ProviderSettingsState()) {

  fun isProviderEnabled(provider: AgentSessionProvider): Boolean {
    return provider.value !in state.disabledProviderIds
  }

  fun setProviderEnabled(provider: AgentSessionProvider, enabled: Boolean) {
    val disabledProviderIds = state.disabledProviderIds
    val updated = if (enabled) disabledProviderIds - provider.value else disabledProviderIds + provider.value
    if (updated == disabledProviderIds) return
    updateState { current -> current.copy(disabledProviderIds = updated) }
    publishChanged()
  }

  fun isProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String): Boolean {
    return providerFeatureStorageId(provider, featureId) !in state.disabledProviderFeatureIds
  }

  fun setProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String, enabled: Boolean) {
    val storageId = providerFeatureStorageId(provider, featureId)
    val disabledFeatureIds = state.disabledProviderFeatureIds
    val updated = if (enabled) disabledFeatureIds - storageId else disabledFeatureIds + storageId
    if (updated == disabledFeatureIds) return
    updateState { current -> current.copy(disabledProviderFeatureIds = updated) }
    publishChanged()
  }

  fun enabledProviders(providers: List<AgentSessionProviderDescriptor>): List<AgentSessionProviderDescriptor> {
    return providers.filter { provider -> isProviderEnabled(provider.provider) }
  }

  fun enabledSessionSources(sources: List<AgentSessionSource>): List<AgentSessionSource> {
    return sources.filter { source -> isProviderEnabled(source.provider) }
  }

  private fun publishChanged() {
    ActivityTracker.getInstance().inc()
    ApplicationManager.getApplication().messageBus.syncPublisher(AgentSessionProviderSettingsListener.TOPIC).providerSettingsChanged()
  }

  @Serializable
  data class ProviderSettingsState(
    @JvmField val disabledProviderIds: Set<String> = emptySet(),
    @JvmField val disabledProviderFeatureIds: Set<String> = emptySet(),
  )
}

private fun providerFeatureStorageId(provider: AgentSessionProvider, featureId: String): String {
  return "${provider.value}:$featureId"
}
