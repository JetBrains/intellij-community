// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.ProjectStoreDescriptor
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

interface IProjectStore : IComponentStore {
  @get:Internal
  val isExternalStorageSupported: Boolean

  val projectBasePath: Path

  @get:Internal
  val locationHash: String

  val storageScheme: StorageScheme

  /**
   * The path to project configuration file - `misc.xml` for directory-based and `*.ipr` for file-based.
   */
  val projectFilePath: Path

  val workspacePath: Path

  @Internal
  fun clearStorages()

  @get:Internal
  @set:Internal
  var isOptimiseTestLoadSpeed: Boolean

  fun isProjectFile(file: VirtualFile): Boolean

  /**
   * The directory of project configuration files for a directory-based project or null for file-based.
   */
  val directoryStorePath: Path?

  @Internal
  fun setPath(file: Path, template: Project?)

  val projectWorkspaceId: String?

  @get:Internal
  val storeDescriptor: ProjectStoreDescriptor

  @Internal
  companion object {
    @TestOnly
    @JvmField
    val COMPONENT_STORE_LOADING_ENABLED: Key<Boolean> = Key.create<Boolean>("COMPONENT_STORE_LOADING_ENABLED")
  }
}
