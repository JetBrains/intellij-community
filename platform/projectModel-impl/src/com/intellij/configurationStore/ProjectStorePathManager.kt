// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * An application service for interacting with project configuration store without an instance of a [com.intellij.openapi.project.Project]
 * In all but a limited number of cases you should use [com.intellij.openapi.project.Project.componentStore]
 */
@ApiStatus.Internal
interface ProjectStorePathManager {
  companion object {
    fun getInstance(): ProjectStorePathManager = service()
  }

  /**
   * Build a path to the project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun getStoreDescriptor(projectRoot: Path): ProjectStoreDescriptor

  /**
   * Query the filesystem for an existing project store directory
   *
   * @param projectRoot root directory of the project
   */
  @RequiresBackgroundThread
  fun testStoreDirectoryExistsForProjectRoot(projectRoot: Path): Boolean {
    return getStoreDescriptor(projectRoot).testStoreDirectoryExistsForProjectRoot()
  }

  /**
   * File a file representing a project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) {
      val fileSystem = projectRoot.fileSystem
      val rootPath = fileSystem.getNioPath(projectRoot) ?: return null
      fileSystem.findFileByPath(getStoreDescriptor(rootPath).dotIdea.toString())?.takeIf { it.isDirectory }
    }
    else {
      null
    }
  }

  /**
   * Query the VFS for an existing project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun testStoreDirectoryExistsForProjectRoot(projectRoot: VirtualFile): Boolean {
    return getStoreDirectory(projectRoot) != null
  }
}