// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.testPSC.impl

import com.intellij.ide.plugins.testPluginSrc.testPSC.MyPersistentComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.Attribute


internal data class MyPersistentState(@Attribute var stateData: String? = "")

@State(name = "MyTestState", storages = [Storage("other.xml")], allowLoadInTests = true)
internal class MyPersistentComponentImpl : MyPersistentComponent,
                                           PersistentStateComponent<MyPersistentState> {

  private var _state = MyPersistentState("")

  override var data: String?
    get() = _state.stateData
    set(value) {
      _state.stateData = value
    }

  override fun getState() = _state

  fun setState(state: MyPersistentState) {
    _state = state
  }

  override fun loadState(state: MyPersistentState) {
    this.state = state
  }
}