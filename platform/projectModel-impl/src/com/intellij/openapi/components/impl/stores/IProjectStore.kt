// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

interface IProjectStore : IComponentStore {
  val projectBasePath: Path

  @get:ApiStatus.Internal
  val locationHash: String

  val projectName: String

  val storageScheme: StorageScheme

  val presentableUrl: String

  /**
   * The path to project configuration file - `misc.xml` for directory-based and `*.ipr` for file-based.
   */
  val projectFilePath: Path

  val workspacePath: Path

  @ApiStatus.Internal
  fun clearStorages()

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var isOptimiseTestLoadSpeed: Boolean

  fun isProjectFile(file: VirtualFile): Boolean

  /**
   * The directory of project configuration files for a directory-based project or null for file-based.
   */
  val directoryStorePath: Path?

  @ApiStatus.Internal
  fun setPath(file: Path, template: Project?)

  val projectWorkspaceId: String?

  @ApiStatus.Internal
  companion object {
    @TestOnly
    @JvmField
    val COMPONENT_STORE_LOADING_ENABLED: Key<Boolean> = Key.create<Boolean>("COMPONENT_STORE_LOADING_ENABLED")
  }
}
