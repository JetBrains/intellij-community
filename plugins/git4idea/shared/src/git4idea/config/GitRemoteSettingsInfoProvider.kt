// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

internal class GitRemoteSettingsInfoProvider : RemoteSettingInfoProvider {
  private val gitSettingsKeyNoDots = GitVcsSettings.SETTINGS_KEY.replace('.', '-')

  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf(gitSettingsKeyNoDots to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend))

  override fun getPluginIdMapping(endpoint: RemoteSettingInfo.Endpoint) = when (endpoint) {
    RemoteSettingInfo.Endpoint.Backend -> mapOf("$GIT_BACKEND_PLUGIN.$gitSettingsKeyNoDots" to GIT_FRONTEND_PLUGIN)
    else -> mapOf("$GIT_FRONTEND_PLUGIN.$gitSettingsKeyNoDots" to GIT_BACKEND_PLUGIN)
  }

  companion object {
    private const val GIT_BACKEND_PLUGIN = "Git4Idea"
    private const val GIT_FRONTEND_PLUGIN = "com.intellij.jetbrains.client.git"
  }
}