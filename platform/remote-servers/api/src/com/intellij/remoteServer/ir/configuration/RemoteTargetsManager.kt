// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Tag

@State(name = "RemoteTargetsManager", storages = [Storage("remote-targets.xml")])
class RemoteTargetsManager : BaseExtendableList<RemoteTargetConfiguration, RemoteTargetType<*>>(RemoteTargetType.EXTENSION_NAME) {

  override fun toBaseState(config: RemoteTargetConfiguration): OneTargetState =
    OneTargetState().also {
      it.loadFromConfiguration(config)
      it.runtimes = config.runtimes.state
    }

  override fun fromOneState(state: BaseExtendableState): RemoteTargetConfiguration? {
    val result = super.fromOneState(state)
    if (result != null && state is OneTargetState) {
      state.runtimes?.let { result.runtimes.loadState(it) }
    }
    return result
  }

  companion object {
    @JvmStatic
    val instance: RemoteTargetsManager = service()
  }

  @Tag("target")
  class OneTargetState : BaseExtendableState() {
    var runtimes by property<ListState>()
  }
}