// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.plugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ChangeReminderUserStorage", storages = [Storage(file = "changeReminder.user.storage.xml")])
class UserSettings : PersistentStateComponent<UserSettings.Companion.State> {
  companion object {
    data class State(var isTurnedOn: Boolean = true)
  }

  private var currentState: UserSettings.Companion.State = State()

  var isTurnedOn: Boolean
    get() = currentState.isTurnedOn
    set(value) {
      currentState.isTurnedOn = value
    }

  override fun loadState(state: State) {
    currentState = state
  }

  override fun getState() = currentState
}