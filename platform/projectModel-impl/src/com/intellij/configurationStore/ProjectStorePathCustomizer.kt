// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
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

  // project dir as passed to setPath if dir (for example, for bazel it is BUILD.bazel, for JPS, it is a parent of .idea)
  val projectIdentityDir: Path
  // project dir as it is expected by a user (e.g., parent of BUILD.bazel)
  val historicalProjectBasePath: Path

  val isDirectoryBased: Boolean
    get() = true

  // where we do store project files (misc.xml and so on), for historical reasons, it must be named as `.idea`
  val dotIdea: Path?

  fun getProjectName(): String

  suspend fun saveProjectName(project: Project)

  fun customMacros(): Map<String, Path> = emptyMap()

  fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage

  /**
   * `storages` are preprocessed by component store - not raw from state spec.
   */
  fun customizeStorageSpecs(
    component: PersistentStateComponent<*>,
    storageManager: StateStorageManager,
    stateSpec: State,
    storages: List<Storage>,
    operation: StateStorageOperation,
  ): List<Storage> = storages
}