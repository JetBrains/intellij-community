// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsConfiguration

internal class VcsRemoteSettingsInfoProvider : RemoteSettingInfoProvider {
  /**
   * Recommended default values are used for VCS settings sync -
   * [RemoteSettingInfo.Direction.InitialFromBackend] for project-level and
   * [RemoteSettingInfo.Direction.InitialFromFrontend] for application-level settings.
   */
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf(
      IssueNavigationConfiguration.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend),
      VcsConfiguration.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend),

      VcsApplicationSettings.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend),
    )
}