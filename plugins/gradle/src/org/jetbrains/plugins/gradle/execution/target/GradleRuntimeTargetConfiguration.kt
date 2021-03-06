// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class GradleRuntimeTargetConfiguration : LanguageRuntimeConfiguration(GradleRuntimeType.TYPE_ID),
                                         PersistentStateComponent<GradleRuntimeTargetConfiguration.MyState> {
  var homePath: String = ""

  override fun getState() = MyState().also {
    it.homePath = this.homePath
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
  }

  class MyState : BaseState() {
    var homePath by string()
  }
}