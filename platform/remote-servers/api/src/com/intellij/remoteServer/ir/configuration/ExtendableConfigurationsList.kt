// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.remoteServer.ir.configuration.BaseExtendableConfiguration.Companion.getTypeImpl

class ExtendableConfigurationsList<C : BaseExtendableConfiguration, T : BaseExtendableType<out C>>(val extPoint: ExtensionPointName<T>) {
  private val resolvedInstances: MutableList<C> = mutableListOf()
  private val unresolvedInstances = mutableListOf<BaseExtendableState>()

  fun clear() {
    resolvedInstances.clear()
    unresolvedInstances.clear()
  }

  fun resolvedConfigs(): List<C> = resolvedInstances.toList()

  fun addConfig(config: C) = resolvedInstances.add(config)

  fun removeConfig(config: C) = resolvedInstances.remove(config)

  fun toListOfStates(): List<BaseExtendableState> {
    return resolvedInstances.map { BaseExtendableState.fromConfiguration(it) } + unresolvedInstances
  }

  fun resetFromListOfStates(states: List<BaseExtendableState>): ExtendableConfigurationsList<C, T> {
    clear()
    states.forEach {
      val nextConfig = fromOneState(it)
      if (nextConfig == null) {
        unresolvedInstances.add(it)
      }
      else {
        resolvedInstances.add(nextConfig)
      }
    }
    return this
  }

  private fun fromOneState(state: BaseExtendableState): C? {
    val type = extPoint.extensionList.firstOrNull { it.id == state.typeId }
    val defaultConfig = type?.createDefaultConfig()
    return defaultConfig?.also {
      it.displayName = state.name ?: ""
      ComponentSerializationUtil.loadComponentState(it.getSerializer(), state.innerState)
    }
  }

  companion object {
    private fun BaseExtendableConfiguration.getSerializer() = getTypeImpl().createSerializer(this)
  }
}