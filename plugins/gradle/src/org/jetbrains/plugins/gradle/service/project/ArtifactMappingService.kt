// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.diagnostic.thisLogger

const val OWNER_BASE_GRADLE: String = "base-gradle"

interface ArtifactMappingService {
  fun getModuleMapping(artifactPath: String): ModuleMappingInfo?

  fun storeModuleId(artifactPath: String, moduleId: String)
  fun storeModuleId(artifactPath: String, moduleId: String, ownerId: String)

  val keys: Collection<String>

  fun putAll(artifactsMap: ArtifactMappingService)
  fun markArtifactPath(canonicalPath: String, hasNonModulesContent: Boolean)
}

interface ModuleMappingInfo {
  val moduleIds: List<String>
  val hasNonModulesContent: Boolean
  val ownerId: String
}

data class ModuleMappingData(override val moduleIds: List<String>,
                             override val hasNonModulesContent: Boolean,
                             override val ownerId: String): ModuleMappingInfo

class MapBasedArtifactMappingService() : ArtifactMappingService {
  constructor(map: Map<String, String>) : this() {
    map.forEach { (k, v) -> storeModuleId(k, v) }
  }

  private val myMap: MutableMap<String, MutableSet<String>> = mutableMapOf()
  private val myOwnersMap: MutableMap<String, String> = mutableMapOf()
  private val myNonModulesContent: MutableSet<String> = HashSet()

  override fun getModuleMapping(artifactPath: String): ModuleMappingInfo? {
    return myMap[artifactPath]?.let { ModuleMappingData(it.toList(), myNonModulesContent.contains(artifactPath), myOwnersMap[artifactPath] ?: OWNER_BASE_GRADLE) }
  }

  override fun storeModuleId(artifactPath: String, moduleId: String) {
    storeModuleId(artifactPath, moduleId, OWNER_BASE_GRADLE)
  }

  override fun storeModuleId(artifactPath: String, moduleId: String, ownerId: String) {
    val previousOwner = myOwnersMap.putIfAbsent(artifactPath, ownerId)
    if (previousOwner != null) {
      thisLogger().debug("[$ownerId] adds new [$moduleId] for [$previousOwner] artifact [$artifactPath]")
    }
    myMap.computeIfAbsent(artifactPath) { LinkedHashSet() }.add(moduleId)
  }

  override val keys: Collection<String>
    get() = myMap.keys

  override fun putAll(artifactsMap: ArtifactMappingService) {
    artifactsMap.keys.forEach { key -> artifactsMap.getModuleMapping(key)?.moduleIds?.let { found -> myMap[key] = LinkedHashSet<String>().also { it.addAll(found) } } }
  }

  override fun markArtifactPath(canonicalPath: String, hasNonModulesContent: Boolean) {
    if (hasNonModulesContent) {
      myNonModulesContent.add(canonicalPath)
    } else {
      myNonModulesContent.remove(canonicalPath)
    }
  }
}