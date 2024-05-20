// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * Tracker of library levels presented in the project.
 * If a module has a library dependency, the library level of this library will be tracked by this service.
 */
@Service(Service.Level.PROJECT)
internal class LibraryLevelsTracker {

  // This is, in fact, a multiset
  private val libraryLevels = Object2IntOpenHashMap<String>().also { it.defaultReturnValue(0) }

  fun dependencyWithLibraryLevelAdded(libraryLevel: String) {
    libraryLevels.addTo(libraryLevel, 1)
  }

  fun dependencyWithLibraryLevelRemoved(libraryLevel: String) {
    val prevValue = libraryLevels.addTo(libraryLevel, -1)
    if (prevValue <= 1) { // It is supposed to be 1 on the last library. However, we'll use <= for extra safety
      libraryLevels.removeInt(libraryLevel)
    }
    if (prevValue <= 0) LOG.error("Unexpected value in library tracker: $prevValue")
  }

  /**
   * Returns true if [libraryLevel] is not presented in any dependency of any module
   */
  fun isNotUsed(libraryLevel: String): Boolean = !libraryLevels.containsKey(libraryLevel)

  /**
   * Returns all library levels that are used by modules
   */
  fun getLibraryLevels(): Set<String> = libraryLevels.keys

  fun clear() {
    libraryLevels.clear()
  }

  companion object {
    fun getInstance(project: Project) = project.service<LibraryLevelsTracker>()
    private val LOG = logger<LibraryLevelsTracker>()
  }
}
