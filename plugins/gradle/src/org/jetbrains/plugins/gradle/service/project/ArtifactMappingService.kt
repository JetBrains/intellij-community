// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.util.SmartList
import com.intellij.util.containers.toMutableSmartList

interface ArtifactMappingService {
  fun getModuleMapping(artifactPath: String): ModuleMappingInfo?

  fun storeModuleId(artifactPath: String, moduleId: String)

  val keys: Collection<String>

  fun putAll(artifactsMap: ArtifactMappingService)
}

interface ModuleMappingInfo {
  val moduleIds: List<String>
}

data class ModuleMappingData(override val moduleIds: List<String>): ModuleMappingInfo {
  fun add(moduleId: String): ModuleMappingInfo = ModuleMappingData(moduleIds + moduleId)
}

class MapBasedArtifactMappingService() : ArtifactMappingService {
  constructor(map: Map<String, String>) : this() {
    map.forEach { (k, v) -> storeModuleId(k, v) }
  }

  private val myMap: MutableMap<String, MutableList<String>> = mutableMapOf()

  override fun getModuleMapping(artifactPath: String): ModuleMappingInfo? {
    return myMap[artifactPath]?.let { ModuleMappingData(it) }
  }

  override fun storeModuleId(artifactPath: String, moduleId: String) {
    myMap.computeIfAbsent(artifactPath) { SmartList() }.add(moduleId)
  }

  override val keys: Collection<String>
    get() = myMap.keys

  override fun putAll(artifactsMap: ArtifactMappingService) {
    artifactsMap.keys.forEach { key -> artifactsMap.getModuleMapping(key)?.moduleIds?.let { found -> myMap[key] = found.toMutableSmartList() } }
  }
}