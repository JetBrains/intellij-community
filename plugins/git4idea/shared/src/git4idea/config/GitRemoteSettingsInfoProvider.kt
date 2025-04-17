// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

internal class GitRemoteSettingsInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf(GitVcsSettings.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromBackend))
}