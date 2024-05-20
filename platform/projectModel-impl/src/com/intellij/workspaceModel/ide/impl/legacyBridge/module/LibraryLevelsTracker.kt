// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.google.common.collect.HashMultiset
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class LibraryLevelsTracker {

  private val libraryLevels = HashMultiset.create<String>() // TODO replace with non-guava multiset

  fun dependencyWithLibraryLevelAdded(libraryLevel: String) {
    libraryLevels.add(libraryLevel)
  }

  fun dependencyWithLibraryLevelRemoved(libraryLevel: String) {
    libraryLevels.remove(libraryLevel)
  }

  fun isEmpty(libraryLevel: String): Boolean = !libraryLevels.contains(libraryLevel)

  fun getLibraryLevels(): Set<String> = libraryLevels.elementSet()

  fun clear() {
    libraryLevels.clear()
  }

  companion object {
    fun getInstance(project: Project) = project.service<LibraryLevelsTracker>()
  }
}
