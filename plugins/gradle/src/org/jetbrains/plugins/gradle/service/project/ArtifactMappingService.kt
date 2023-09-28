// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.diagnostic.thisLogger

const val OWNER_BASE_GRADLE: String = "base-gradle"

/**
 * A service for managing artifact mappings.
 *
 * The service manages relation of artifacts (e.g., jar files) and modules
 *
 * For each artifact path, the service can store and provide:
 *  * list of modules that contribute to the content of this artifact (e.g., compilation output)
 *  * artifact owner identification for complex build setups (Android, KMP, Kotlin)
 *  * a property to notify the platform that artifact has non-module entries inside (e.g., relocated transitive dependencies)
 *
 * Ownership information is set with first [storeModuleId] call and is **not** updated.
 */
interface ArtifactMappingService {
  /**
   * Get available mapping information.
   *
   * @param artifactPath - artifact canonical path
   */
  fun getModuleMapping(artifactPath: String): ModuleMappingInfo?


  /**
   * Stores the given module ID for the specified artifact path.
   *
   * The ownership will be set to [OWNER_BASE_GRADLE]
   * @param artifactPath - artifact canonical path
   * @param moduleId - module ID of corresponding [com.intellij.openapi.externalSystem.model.project.ModuleData]
   */
  fun storeModuleId(artifactPath: String, moduleId: String)

  /**
   * Stores the given module ID for the specified artifact path and sets an owner to the specified ownerId.
   *
   * Owner is only set when the artifact path is stored for the first time
   * @param artifactPath - artifact canonical path
   * @param moduleId - module ID of corresponding [com.intellij.openapi.externalSystem.model.project.ModuleData]
   */
  fun storeModuleId(artifactPath: String, moduleId: String, ownerId: String)

  /**
   * List all available artifact paths
   */
  val keys: Collection<String>

  /**
   * Store all mapping information from another service instance
   *
   * @param artifactsMap information to import
   */
  fun putAll(artifactsMap: ArtifactMappingService)

  /**
   * Mark given artifact as having not only modules, but something else that can potentially contribute to classpath
   *
   * For example, relocated (Shadow Jar) or repackaged (Fat Jar) dependencies
   */
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
    artifactsMap.keys.forEach { key ->
      artifactsMap.getModuleMapping(key)?.let { mapping ->
        myMap[key] = LinkedHashSet<String>().also { it.addAll(mapping.moduleIds) }
        myOwnersMap[key] = mapping.ownerId
        if (mapping.hasNonModulesContent) {
          myNonModulesContent.add(key)
        }
      }
    }
  }

  override fun markArtifactPath(canonicalPath: String, hasNonModulesContent: Boolean) {
    if (hasNonModulesContent) {
      myNonModulesContent.add(canonicalPath)
    } else {
      myNonModulesContent.remove(canonicalPath)
    }
  }
}