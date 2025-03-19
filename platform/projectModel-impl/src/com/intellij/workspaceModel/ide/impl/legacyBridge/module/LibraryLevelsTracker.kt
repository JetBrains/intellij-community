// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus

/**
 * Tracker of library levels presented in the project.
 * If a module has a library dependency, the library level of this library will be tracked by this service.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class LibraryLevelsTracker {

  private val libraryLevels = MultiSet<String>()

  fun dependencyWithLibraryLevelAdded(libraryLevel: String, occurrences: Int) {
    libraryLevels.add(libraryLevel, occurrences)
  }

  fun dependencyWithLibraryLevelRemoved(libraryLevel: String, occurrences: Int) {
    val prevValue = libraryLevels.remove(libraryLevel, occurrences)
    if (prevValue <= 0) LOG.error("Unexpected value in library tracker: $prevValue for $libraryLevel. Tried to add $occurrences occurrences")
  }

  /**
   * Returns true if [libraryLevel] is not presented in any dependency of any module
   */
  fun isNotUsed(libraryLevel: String): Boolean = libraryLevel !in libraryLevels

  /**
   * Returns all library levels that are used by modules
   */
  fun getLibraryLevels(): Set<String> = libraryLevels.items()

  fun clear() {
    libraryLevels.clear()
  }

  companion object {
    fun getInstance(project: Project) = project.service<LibraryLevelsTracker>()
    fun getInstanceIfInitialized(project: Project): LibraryLevelsTracker? = project.serviceIfCreated<LibraryLevelsTracker>()
    private val LOG = logger<LibraryLevelsTracker>()
  }
}

/**
 * A wrapper over Object2IntOpenHashMap that works like multiset
 */
internal class MultiSet<T> : Iterable<T> {
  private val storage = Object2IntOpenHashMap<T>().also { it.defaultReturnValue(0) }

  /**
   * Return number of occurrences BEFORE adding
   */
  fun add(value: T, occurrences: Int): Int {
    require(occurrences >= 0)
    return storage.addTo(value, occurrences)
  }

  /**
   * Return number of occurrences BEFORE adding
   */
  fun add(value: T): Int = storage.addTo(value, 1)

  /**
   * Return number of occurrences BEFORE removal
   */
  fun remove(value: T, occurrences: Int): Int {
    require(occurrences >= 0)
    val prevValue = storage.addTo(value, -occurrences)
    if (prevValue <= 1) storage.removeInt(value) // In fact, this should not go under 1, but let's use <= for extra safety
    return prevValue
  }

  /**
   * Return number of occurrences BEFORE removal
   */
  fun remove(value: T): Int {
    val prevValue = storage.addTo(value, -1)
    if (prevValue <= 1) storage.removeInt(value) // In fact, this should not go under 1, but let's use <= for extra safety
    return prevValue
  }

  operator fun contains(value: T) = storage.containsKey(value)
  fun items() = storage.keys
  fun clear() = storage.clear()

  /**
   * Iterates over distinct values
   */
  override fun iterator(): Iterator<T> = items().iterator()

  inline fun forEachWithOccurrences(action: (T, Int) -> Unit) {
    storage.forEach { (key, occ) -> action(key, occ) }
  }
}
