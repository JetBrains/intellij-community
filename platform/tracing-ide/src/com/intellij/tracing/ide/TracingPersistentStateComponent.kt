// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(name = "tracing", storages = [(Storage(value = "tracing.xml"))])
internal class TracingPersistentStateComponent : SimplePersistentStateComponent<TracingPersistentStateComponent.State>(State()) {
  companion object {
    fun getInstance(): TracingPersistentStateComponent = ApplicationManager.getApplication().getService(TracingPersistentStateComponent::class.java)
  }

  class State : BaseState() {
    var isEnabled by property(false)
  }
}