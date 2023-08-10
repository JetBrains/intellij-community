// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import java.util.*

@State(name = "ChangeReminder", storages = [Storage("changeReminder.xml")], category = SettingsCategory.TOOLS)
internal class UserSettings : SimplePersistentStateComponent<UserSettingsState>(UserSettingsState()) {
  private val eventDispatcher = EventDispatcher.create(PluginStatusListener::class.java)

  var isPluginEnabled: Boolean
    get() = state.isPluginEnabled
    set(value) {
      state.isPluginEnabled = value
      eventDispatcher.multicaster.statusChanged(value)
    }

  fun addPluginStatusListener(listener: PluginStatusListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  interface PluginStatusListener : EventListener {
    fun statusChanged(isEnabled: Boolean)
  }
}

internal class UserSettingsState : BaseState() {
  var isPluginEnabled: Boolean by property(false)
}