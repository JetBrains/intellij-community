// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XCollection

typealias TargetsList = ExtendableConfigurationsList<RemoteTargetConfiguration, RemoteTargetType<*>>

@State(name = "RemoteTargetsManager", storages = [Storage("remote-targets.xml")])
class RemoteTargetsManager : PersistentStateComponent<RemoteTargetsManager.AllTargetsState> {
  private val _targets = TargetsList(RemoteTargetType.EXTENSION_NAME)

  override fun getState(): AllTargetsState = AllTargetsState().apply {
    targets.addAll(_targets.toListOfStates())
  }

  override fun loadState(state: AllTargetsState) {
    _targets.clear()
    _targets.resetFromListOfStates(state.targets)
  }

  val allConfigs: List<RemoteTargetConfiguration>
    get() = _targets.resolvedConfigs()

  //fun <C : RemoteTargetConfiguration> configsForType(type: RemoteTargetType<C>): List<C> =
  //  allConfigs.filter { it.typeId == type.id }.map { type.castConfiguration(it) }

  internal fun addTarget(target: RemoteTargetConfiguration) = _targets.addConfig(target)

  internal fun removeTarget(target: RemoteTargetConfiguration) = _targets.removeConfig(target)

  companion object {
    @JvmStatic
    val instance: RemoteTargetsManager = ServiceManager.getService(RemoteTargetsManager::class.java)
  }

  class AllTargetsState : BaseState() {
    @get: XCollection(style = XCollection.Style.v2)
    var targets by list<BaseExtendableState>()
  }

}