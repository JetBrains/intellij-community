// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.models

import com.intellij.ide.RecentProjectMetaInfo

open class Settings(
  val preferences: SettingsPreferences = SettingsPreferences(),

  var laf: ILookAndFeel? = null,
  var syntaxScheme: EditorColorScheme? = null,
  var keymap: Keymap? = null,
  /**
   * Original plugin id ⇒ feature or plugin info.
   */
  val plugins: MutableMap<String, FeatureInfo> = mutableMapOf(),
  /**
   * Don't forget to set info.projectOpenTimestamp
   */
  val recentProjects: MutableList<RecentPathInfo> = mutableListOf()
) {
  val notes = mutableMapOf<String, Any>()
}

data class RecentPathInfo(
  val path: String,
  val info: RecentProjectMetaInfo
)
