// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * For cases when the project configuration store resides in the project root directory - the default
 */
internal class NestedProjectStorePathManager : ProjectStorePathManager {
  override fun getStoreDirectoryPath(projectRoot: Path): Path = projectRoot.resolve(NESTED_STORE_DIRECTORY_NAME)

  override fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) projectRoot.findChild(NESTED_STORE_DIRECTORY_NAME) else null
  }

  companion object {
    private const val NESTED_STORE_DIRECTORY_NAME: String = Project.DIRECTORY_STORE_FOLDER
  }
}