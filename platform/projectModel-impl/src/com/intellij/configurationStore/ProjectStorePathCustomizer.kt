// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
@Experimental
interface ProjectStorePathCustomizer {
  fun getStoreDirectoryPath(projectRoot: Path): ProjectStoreDescriptor?
}

@Internal
@Experimental
interface ProjectStoreDescriptor {
  val isExternalStorageSupported: Boolean
    get() = false

  // project file as passed to setPath (for example, for bazel it is BUILD.bazel, for JPS, it is a parent of .idea)
  val projectIdentityFile: Path
  // project dir as it is expected by a user (e.g., parent of BUILD.bazel)
  val historicalProjectBasePath: Path

  // where we do store project files (misc.xml and so on)
  val dotIdea: Path?

  fun removeProjectConfigurationAndCaches() {
    for (file in NioFiles.list(dotIdea!!)) {
      NioFiles.deleteRecursively(file)
    }
  }

  val presentableUrl: Path
    get() = projectIdentityFile

  fun testStoreDirectoryExistsForProjectRoot(): Boolean

  val projectName: @NlsSafe String
    get() = NioFiles.getFileName(historicalProjectBasePath)

  suspend fun saveProjectName(project: Project) {}

  fun customMacros(): Map<String, Path> = emptyMap()

  fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage

  fun getModuleStorageSpecs(
    component: PersistentStateComponent<*>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
    project: Project,
  ): List<Storage>

  fun <T : Any> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
  ): List<Storage>
}

@Internal
val deprecatedStorageComparator: Comparator<Storage> = Comparator { o1, o2 ->
  val w1 = if (o1.deprecated) 1 else 0
  val w2 = if (o2.deprecated) 1 else 0
  w1 - w2
}

@Internal
fun sortStoragesByDeprecated(storages: List<Storage>): List<Storage> {
  if (storages.size < 2) {
    return storages.toList()
  }

  if (!storages.first().deprecated) {
    val othersAreDeprecated = (1 until storages.size).any { storages.get(it).deprecated }
    if (othersAreDeprecated) {
      return storages
    }
  }

  return storages.sortedWith(deprecatedStorageComparator)
}
