// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.target

import com.intellij.openapi.components.*
import com.intellij.remoteServer.ir.config.BaseExtendableList
import com.intellij.remoteServer.ir.config.BaseExtendableState
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "RemoteTargetsManager", storages = [Storage("remote-targets.xml")])
class RemoteTargetsManager : PersistentStateComponent<RemoteTargetsManager.TargetsListState> {

  val targets: BaseExtendableList<RemoteTargetConfiguration, RemoteTargetType<*>> = TargetsList()

  override fun getState(): RemoteTargetsManager.TargetsListState {
    val result = TargetsListState()
    for (next in this.targets.state.configs) {
      result.targets.add(next as OneTargetState)
    }
    return result
  }

  override fun loadState(state: RemoteTargetsManager.TargetsListState) {
    targets.loadState(state.targets)
  }

  companion object {
    @JvmStatic
    val instance: RemoteTargetsManager = service()
  }

  internal class TargetsList : BaseExtendableList<RemoteTargetConfiguration, RemoteTargetType<*>>(RemoteTargetType.EXTENSION_NAME) {
    override fun toBaseState(config: RemoteTargetConfiguration): OneTargetState =
      OneTargetState().also {
        it.loadFromConfiguration(config)
        it.runtimes = config.runtimes.state.configs
      }

    override fun fromOneState(state: BaseExtendableState): RemoteTargetConfiguration? {
      val result = super.fromOneState(state)
      if (result != null && state is OneTargetState) {
        result.runtimes.loadState(state.runtimes)
      }
      return result
    }

  }

  class TargetsListState : BaseState() {
    @get: XCollection(style = XCollection.Style.v2)
    var targets by list<OneTargetState>()
  }


  @Tag("target")
  class OneTargetState : BaseExtendableState() {
    @get: XCollection(style = XCollection.Style.v2)
    @get: Property(surroundWithTag = false)
    var runtimes by list<BaseExtendableState>()
  }
}