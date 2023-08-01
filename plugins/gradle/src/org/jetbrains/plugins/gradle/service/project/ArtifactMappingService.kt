// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

interface ArtifactMappingService {
  fun getModuleMapping(artifactPath: String): ModuleMappingInfo?

  fun storeModuleId(artifactPath: String, moduleId: String)

  val keys: Collection<String>

  operator fun set(artifactPath: String, moduleId: String) = put(artifactPath, moduleId) // --> getModuleId

  fun put(artifactPath: String, moduleId: String)  // --> storeModuleId

  fun putAll(artifactsMap: ArtifactMappingService)
  fun putAll(artifactsMap: Map<String, String>)
}

interface ModuleMappingInfo {
  val moduleId: String
}

data class ModuleMappingData(override val moduleId: String): ModuleMappingInfo

class MapBasedArtifactMappingService(private val myMap: MutableMap<String, String>) : ArtifactMappingService {

  override fun getModuleMapping(artifactPath: String): ModuleMappingInfo? {
    return myMap[artifactPath]?.let { ModuleMappingData(it) }
  }

  override fun storeModuleId(artifactPath: String, moduleId: String) {
    myMap[artifactPath] = moduleId
  }

  override val keys: Collection<String>
    get() = myMap.keys

  override fun put(artifactPath: String, moduleId: String) = storeModuleId(artifactPath, moduleId)
  override fun putAll(artifactsMap: ArtifactMappingService) {
    artifactsMap.keys.forEach { key -> artifactsMap.getModuleMapping(key)?.moduleId?.let { found -> myMap[key] = found } }
  }

  override fun putAll(artifactsMap: Map<String, String>) {
    myMap.putAll(artifactsMap)
  }
}