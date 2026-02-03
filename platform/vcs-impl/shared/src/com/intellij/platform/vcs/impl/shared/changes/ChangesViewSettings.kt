// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangesViewSettings {
  var groupingKeys: Collection<String>
  var showIgnored: Boolean

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangesViewSettings = project.service()
  }
}

@State(name = "ChangesViewManager", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class ChangesViewSettingsImpl : SimplePersistentStateComponent<ChangesViewSettingsImpl.State>(State()), ChangesViewSettings {
  override var groupingKeys: Collection<String>
    get() = state.groupingKeys
    set(value) {
      state.groupingKeys = value.toMutableSet()
    }

  override var showIgnored: Boolean
    get() = state.showIgnored
    set(value) {
      state.showIgnored = value
    }

  internal class State() : BaseState() {
    @get:XCollection
    var groupingKeys: MutableSet<String> by stringSet(ChangesGroupingSupport.REPOSITORY_GROUPING)

    @get:Attribute("show_ignored")
    var showIgnored by property(false)
  }
}