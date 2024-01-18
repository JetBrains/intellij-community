// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.openapi.components.*
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@State(name = "StatusBar", category = SettingsCategory.UI, storages = [Storage(value = "ide.general.xml")])
class StatusBarWidgetSettings : SerializablePersistentStateComponent<StatusBarState>(StatusBarState()) {
  companion object {
    @JvmStatic
    fun getInstance(): StatusBarWidgetSettings = service()
  }

  fun isExplicitlyDisabled(id: String): Boolean = state.widgets.get(id) == false

  fun isEnabled(factory: StatusBarWidgetFactory): Boolean {
    return state.widgets.get(factory.id) ?: factory.isEnabledByDefault
  }

  fun setEnabled(factory: StatusBarWidgetFactory, newValue: Boolean) {
    if (factory.isEnabledByDefault == newValue) {
      updateState {
        StatusBarState(it.widgets - factory.id)
      }
    }
    else {
      updateState {
        StatusBarState(it.widgets + (factory.id to newValue))
      }
    }
  }
}

data class StatusBarState(@JvmField val widgets: Map<String, Boolean> = emptyMap())
