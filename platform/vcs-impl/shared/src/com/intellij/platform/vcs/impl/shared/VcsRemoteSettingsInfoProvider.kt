// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider
import com.intellij.openapi.vcs.IssueNavigationConfiguration

internal class VcsRemoteSettingsInfoProvider  : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf(IssueNavigationConfiguration.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromBackend))
}