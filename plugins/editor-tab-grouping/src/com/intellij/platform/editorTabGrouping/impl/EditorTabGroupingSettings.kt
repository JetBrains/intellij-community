// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(name = "EditorTabGroupingSettings", storages = [Storage("editor.xml")], category = SettingsCategory.UI)
internal class EditorTabGroupingSettings : SimplePersistentStateComponent<EditorTabGroupingSettings.State>(State()) {
  internal class State : BaseState() {
    var groupByDirectory: Boolean by property(true)
    var showGroupNames: Boolean by property(false)
    var collapseGroups: Boolean by property(false)
    var groupLabelStyleOrdinal: Int by property(GroupLabelStyle.RAIL.ordinal)
  }

  var groupByDirectory: Boolean
    get() = state.groupByDirectory
    set(value) {
      state.groupByDirectory = value
    }

  var showGroupNames: Boolean
    get() = state.showGroupNames
    set(value) {
      state.showGroupNames = value
    }

  /** Collapses a tab group into one tab when the user selects a tab outside it. */
  var collapseGroups: Boolean
    get() = state.collapseGroups
    set(value) {
      state.collapseGroups = value
    }

  var groupLabelStyle: GroupLabelStyle
    get() = GroupLabelStyle.entries.getOrElse(state.groupLabelStyleOrdinal) { GroupLabelStyle.RAIL }
    set(value) {
      state.groupLabelStyleOrdinal = value.ordinal
    }

  companion object {
    fun getInstance(): EditorTabGroupingSettings =
      ApplicationManager.getApplication().getService(EditorTabGroupingSettings::class.java)
  }
}

/**
 * The way the editor tabs paint the label of a group.
 */
internal enum class GroupLabelStyle {
  /** A rounded outline around the whole group. */
  OUTLINE,

  /** A line above the group. */
  RAIL,
}
