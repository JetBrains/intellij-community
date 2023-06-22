// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile

interface WebSymbolsContextSourceProximityProvider {

  fun calculateProximity(project: Project, dir: VirtualFile, sourceNames: Set<String>, sourceKind: SourceKind): Result

  data class Result(val dependency2proximity: Map<String, Double> = emptyMap(),
                    val modificationTrackers: Collection<ModificationTracker> = emptyList())

  enum class SourceKind {
    PackageManagerDependency,
    IdeLibrary,
    ProjectToolExecutable,
  }

  companion object {
    @JvmStatic
    fun mergeProximity(a: Double?, b: Double): Double =
      a?.coerceAtMost(b) ?: b

    private val EP_NAME = ExtensionPointName<WebSymbolsContextSourceProximityProvider>(
      "com.intellij.webSymbols.contextSourceProximityProvider")

    internal fun calculateProximity(project: Project,
                                    dir: VirtualFile,
                                    sourceNames: Set<String>,
                                    sourceKind: SourceKind): Result {
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
      return Result(dependency2proximity, trackers)
    }

  }

}