// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element

@State(name = "RemoteTargetsManager", storages = [Storage("remote-targets.xml")])
class RemoteTargetsManager : PersistentStateComponent<RemoteTargetsManager.AllTargetsState> {
  private val resolvedTargets = mutableListOf<RemoteTargetConfiguration>()
  private val unresolvedTargets = mutableListOf<OneTargetState>()

  override fun getState(): AllTargetsState = AllTargetsState().apply {
    targets.addAll(resolvedTargets.map { OneTargetState.fromConfiguration(it) })
    targets.addAll(unresolvedTargets)
  }

  override fun loadState(state: AllTargetsState) {
    resolvedTargets.clear()
    unresolvedTargets.clear()

    state.targets.forEach {
      val nextConfig = it.toConfiguration()
      if (nextConfig == null) {
        unresolvedTargets.add(it)
      }
      else {
        resolvedTargets.add(nextConfig)
      }
    }
  }

  val allConfigs: List<RemoteTargetConfiguration>
    get() = resolvedTargets.toList()

  //fun <C : RemoteTargetConfiguration> configsForType(type: RemoteTargetType<C>): List<C> =
  //  allConfigs.filter { it.typeId == type.id }.map { type.castConfiguration(it) }

  internal fun addTarget(target: RemoteTargetConfiguration) {
    resolvedTargets.add(target)
  }

  internal fun removeTarget(target: RemoteTargetConfiguration) {
    resolvedTargets.remove(target)
  }

  companion object {
    @JvmStatic
    val instance: RemoteTargetsManager = ServiceManager.getService(RemoteTargetsManager::class.java)
  }

  class AllTargetsState : BaseState() {
    @get: XCollection(style = XCollection.Style.v2)
    var targets by list<OneTargetState>()
  }

  class OneTargetState : BaseState() {
    @get:Attribute("type")
    var typeId by string()

    @get:Attribute("name")
    var name by string()

    @get:Tag("config")
    var innerState: Element? by property<Element?>(null) { it === null }

    internal fun toConfiguration(): RemoteTargetConfiguration? {
      val defaultConfig = typeId?.let { RemoteTargetType.findTargetType(it) }?.createDefaultConfig()
      return defaultConfig?.also {
        it.displayName = name ?: ""
        ComponentSerializationUtil.loadComponentState(it.getSerializer(), innerState)
      }
    }

    companion object {
      @JvmStatic
      internal fun fromConfiguration(config: RemoteTargetConfiguration) = OneTargetState().apply {
        typeId = config.typeId
        name = config.displayName
        innerState = config.getSerializer().state?.let { XmlSerializer.serialize(it) }
      }

      private fun RemoteTargetConfiguration.getSerializer() = this.getTargetType().createSerializer(this)
    }
  }

}