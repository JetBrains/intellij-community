// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
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
        it.copy(widgets = it.widgets - factory.id)
      }
    }
    else {
      updateState {
        it.copy(widgets = it.widgets + (factory.id to newValue))
      }
    }
  }

  fun setUserMoves(userMoves: List<StatusBarWidgetUserMove>) {
    updateState { state ->
      state.copy(userMoves = userMoves.associate { it.source to it.target })
    }
  }

  fun getUserMoves(): List<StatusBarWidgetUserMove> {
    return state.userMoves.map { StatusBarWidgetUserMove(it.key, it.value) }
  }
}

@Internal
data class StatusBarState(
  @JvmField val widgets: Map<String, Boolean> = emptyMap(),
  @JvmField val userMoves: Map<String, String> = emptyMap(),
)

@Internal
data class StatusBarWidgetUserMove(
  val source: String,
  val target: String,
)
