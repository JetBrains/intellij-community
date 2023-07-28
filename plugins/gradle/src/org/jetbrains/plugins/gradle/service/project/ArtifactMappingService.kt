// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

interface ArtifactMappingService {
  fun getModuleId(artifactPath: String): String?

  fun storeModuleId(artifactPath: String, moduleId: String)

  val keys: Collection<String>

  operator fun get(artifactPath: String?): String? // --> getModuleId
  operator fun set(artifactPath: String, moduleId: String) = put(artifactPath, moduleId) // --> getModuleId

  fun put(artifactPath: String, moduleId: String)  // --> storeModuleId

  fun putAll(artifactsMap: ArtifactMappingService)
  fun putAll(artifactsMap: Map<String, String>)
}

class MapBasedArtifactMappingService(private val myMap: MutableMap<String, String>) : ArtifactMappingService {

  override fun getModuleId(artifactPath: String): String? {
    return myMap[artifactPath]
  }

  override fun storeModuleId(artifactPath: String, moduleId: String) {
    myMap[artifactPath] = moduleId
  }

  override val keys: Collection<String>
    get() = myMap.keys

  override fun get(artifactPath: String?): String? = artifactPath?.let{ getModuleId(it) }
  override fun put(artifactPath: String, moduleId: String) = storeModuleId(artifactPath, moduleId)
  override fun putAll(artifactsMap: ArtifactMappingService) {
    artifactsMap.keys.forEach { key -> artifactsMap.getModuleId(key)?.let { found -> myMap[key] = found } }
  }

  override fun putAll(artifactsMap: Map<String, String>) {
    myMap.putAll(artifactsMap)
  }
}