// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Keeps the [ComposeResourcePreviewBackground] that the user selects for each drawable file. */
@Service(Service.Level.PROJECT)
@State(
  name = "ComposeResourcePreviewBackground",
  storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],
)
internal class ComposeResourcePreviewBackgroundSettings
  : SimplePersistentStateComponent<ComposeResourcePreviewBackgroundSettings.SettingsState>(SettingsState()) {

  internal class SettingsState : BaseState() {
    var fileUrlToBackground: MutableMap<String, ComposeResourcePreviewBackground> by map()
    var lastSelectedBackground: ComposeResourcePreviewBackground by enum(ComposeResourcePreviewBackground.NONE)
  }

  fun loadBackground(file: VirtualFile): ComposeResourcePreviewBackground =
    state.fileUrlToBackground[file.url] ?: state.lastSelectedBackground

  fun saveBackground(file: VirtualFile, background: ComposeResourcePreviewBackground) {
    state.lastSelectedBackground = background
    state.fileUrlToBackground[file.url] = background
  }

  companion object {
    fun getInstance(project: Project): ComposeResourcePreviewBackgroundSettings = project.service()
  }
}
