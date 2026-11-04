// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

internal class ProjectViewPaneRemoteSettingsProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> {
    return mapOf(
      "UISettings.OPEN_IN_PREVIEW_TAB_IF_POSSIBLE" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend),
      "ProjectViewState" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend),
      // ProjectViewFrontendState owns these options. Each side keeps its own copy in ProjectViewState.
      "ProjectViewState.autoscrollToSource" to RemoteSettingInfo(RemoteSettingInfo.Direction.DoNotSynchronize),
      "ProjectViewState.openDirectoriesWithSingleClick" to RemoteSettingInfo(RemoteSettingInfo.Direction.DoNotSynchronize),
      "ProjectViewState.autoscrollFromSource" to RemoteSettingInfo(RemoteSettingInfo.Direction.DoNotSynchronize),
      "ProjectViewFrontendState" to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromFrontend),
    )
  }
}
