// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.EventDispatcher
import java.util.*

@State(name = "ChangeReminderUserStorage", storages = [Storage(file = "changeReminder.user.storage.xml")])
class UserSettings : PersistentStateComponent<UserSettings.Companion.State> {
  companion object {
    data class State(var isPluginEnabled: Boolean = true)
  }

  interface PluginStatusListener : EventListener {
    fun statusChanged(isEnabled: Boolean)
  }

  private val eventDispatcher = EventDispatcher.create(PluginStatusListener::class.java)
  private var currentState = State()

  var isPluginEnabled: Boolean
    get() = currentState.isPluginEnabled
    set(value) {
      currentState.isPluginEnabled = value
      eventDispatcher.multicaster.statusChanged(value)
    }

  override fun loadState(state: State) {
    currentState = state
  }

  override fun getState(): State = currentState

  fun addPluginStatusListener(listener: PluginStatusListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }
}