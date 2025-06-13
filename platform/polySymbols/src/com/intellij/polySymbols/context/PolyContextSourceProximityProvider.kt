// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly

interface PolyContextSourceProximityProvider {

  fun calculateProximity(project: Project, dir: VirtualFile, sourceNames: Set<String>, sourceKind: SourceKind): Result

  sealed interface Result {
    val dependency2proximity: Map<String, Double>
    val modificationTrackers: Collection<ModificationTracker>

    companion object {
      @JvmStatic
      val empty: Result = PolyContextSourceProximityProviderResultData(emptyMap(), emptySet())

      @JvmStatic
      fun create(dependency2proximity: Map<String, Double>, modificationTrackers: Collection<ModificationTracker>): Result =
        PolyContextSourceProximityProviderResultData(dependency2proximity, modificationTrackers)
    }
  }

  sealed interface SourceKind {

    data object IdeLibrary : SourceKind

    data object ProjectToolExecutable : SourceKind

    data class PackageManagerDependency internal constructor(val name: String) : SourceKind
  }

  @Suppress("TestOnlyProblems")
  companion object {
    @JvmStatic
    fun mergeProximity(a: Double?, b: Double): Double =
      a?.coerceAtMost(b) ?: b

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolyContextSourceProximityProvider> =
      ExtensionPointName<PolyContextSourceProximityProvider>("com.intellij.polySymbols.contextSourceProximityProvider")

    internal fun calculateProximity(
      project: Project,
      dir: VirtualFile,
      sourceNames: Set<String>,
      sourceKind: SourceKind,
    ): Result {
      val dependency2proximity = mutableMapOf<String, Double>()
      val trackers = mutableSetOf<ModificationTracker>()
      EP_NAME.extensionList
        .map { it.calculateProximity(project, dir, sourceNames, sourceKind) }
        .forEach {
          it.dependency2proximity.forEach { (name, proximity) ->
            dependency2proximity.merge(name, proximity, Companion::mergeProximity)
          }
          trackers.addAll(it.modificationTrackers)
        }
      return Result.create(dependency2proximity, trackers)
    }

  }

}

private data class PolyContextSourceProximityProviderResultData(
  override val dependency2proximity: Map<String, Double>,
  override val modificationTrackers: Collection<ModificationTracker>,
) : PolyContextSourceProximityProvider.Result