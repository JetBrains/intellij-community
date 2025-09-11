// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * For cases when the project configuration store resides in the project root directory - the default
 */
internal class NestedProjectStorePathManager : ProjectStorePathManager {
  override fun getStoreDescriptor(projectRoot: Path): ProjectStorePathCustomizer.StoreDescriptor {
    ProjectStorePathCustomizer.EP_NAME.lazySequence().firstNotNullOfOrNull { it.getStoreDirectoryPath(projectRoot) }?.let {
      return it
    }
    val useParent = System.getProperty("store.basedir.parent.detection", "true").toBoolean() &&
                    (projectRoot.fileName?.toString()?.startsWith("${DIRECTORY_STORE_FOLDER}.") == true)
    return ProjectStorePathCustomizer.StoreDescriptor(
      projectIdentityDir = projectRoot,
      dotIdea = projectRoot.resolve(DIRECTORY_STORE_FOLDER),
      historicalProjectBasePath = if (useParent) projectRoot.parent.parent else projectRoot,
    )
  }

  override fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) projectRoot.findChild(DIRECTORY_STORE_FOLDER) else null
  }
}