// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.models

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.util.PlatformUtils

open class Settings(
  val preferences: SettingsPreferences = SettingsPreferences(),

  var laf: ILookAndFeel? = null,
  var syntaxScheme: EditorColorScheme? = null,
  var keymap: Keymap? = null,
  /**
   * Original plugin id ⇒ feature or plugin info.
   */
  val plugins: MutableMap<String, FeatureInfo> = mutableMapOf(),
) {
  val notes = mutableMapOf<String, Any>()

  private val recentProjectLimit =
    if (PlatformUtils.isWebStorm()) 5
    else 10

  private val recentProjectList = mutableListOf<RecentPathInfo>()
  val recentProjects: List<RecentPathInfo> = recentProjectList

  /**
   * NOTE: Remember to set info.projectOpenTimestamp.
   *
   * @return true if there is still space for new projects, false otherwise.
   */
  fun addRecentProjectIfNeeded(info: () -> RecentPathInfo?): Boolean {
    if (recentProjects.size < recentProjectLimit) {
      info()?.let(recentProjectList::add)
    }

    return recentProjects.size < recentProjectLimit
  }
}

data class RecentPathInfo(
  val path: String,
  val info: RecentProjectMetaInfo
)
