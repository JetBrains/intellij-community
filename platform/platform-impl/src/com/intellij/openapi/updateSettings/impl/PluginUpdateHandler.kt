// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginUpdateHandler {
  fun isEnabled(): Boolean
  suspend fun loadAndStorePluginUpdates(apiVersion: String?, sessionId: String = UUID.randomUUID().toString(), indicator: ProgressIndicator? = null): PluginUpdateModel
  suspend fun installUpdates(sessionId: String, updates: List<PluginUiModel>, component: JComponent?, finishCallback: Runnable?)

  suspend fun ignorePluginUpdates(sessionId: String)

  companion object {
    val EP_NAME = ExtensionPointName.create<PluginUpdateHandler>("com.intellij.pluginUpdateHandler")

    fun getInstance(): PluginUpdateHandler = EP_NAME.extensionList.firstOrNull { it.isEnabled() } ?: DefaultPluginUpdateHandler()
  }
}

@ApiStatus.Internal
@Serializable
data class PluginUpdateModel(
  val sessionId: String,
  @Transient private val nonIgnoredUpdates: List<PluginUiModel> = emptyList(),
  val incompatiblePluginNames: List<String>,
  @Transient private val customRepoPluginUpdates: List<PluginUiModel> = emptyList(),
  val internalErrors: Map<String?, String>,
) {
  val notIgnoredDtos: List<PluginDto> = nonIgnoredUpdates.map { PluginDto.fromModel(it) }
  val customRepoDtos: List<PluginDto> = customRepoPluginUpdates.map { PluginDto.fromModel(it) }

  @Transient
  var downloaders: List<PluginDownloader> = emptyList() //compatibility parameter that shouldn't exist in the future

  fun getNotIgnoredUpdates(): List<PluginUiModel> = nonIgnoredUpdates.ifEmpty { notIgnoredDtos }
  fun getCustomRepoUpdates(): List<PluginUiModel> = customRepoPluginUpdates.ifEmpty { customRepoDtos }
}