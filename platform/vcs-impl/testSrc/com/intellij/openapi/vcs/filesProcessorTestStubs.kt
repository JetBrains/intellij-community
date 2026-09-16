// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.vcs.MockChangeListManager
import kotlin.io.path.createDirectories

/**
 * Creates the project configuration directory on the disk and puts it into the VFS.
 *
 * A files processor looks for the directory to tell a project configuration file from a source file.
 * A fixture project does not create the directory, so a test must create it.
 */
internal fun createProjectConfigDir(project: Project): VirtualFile {
  val storePath = requireNotNull(project.stateStore.directoryStorePath) { "the project must be directory based" }
  storePath.createDirectories()
  return requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(storePath)) {
    "the project configuration directory must be in the VFS: $storePath"
  }
}

/**
 * A [FilePath] that counts how many times it resolved to a [VirtualFile].
 *
 * A files processor must apply every cheap filter before it resolves a path.
 * A project can hold hundreds of thousands of unversioned paths, and every resolve reads the VFS.
 * A test asserts the count is zero for a path that a filter drops.
 */
internal class CountingFilePath(path: String) : LocalFilePath(path, false) {
  val file: VirtualFile = LightVirtualFile(path)

  var resolveCount: Int = 0
    private set

  override fun findFile(path: String): VirtualFile {
    resolveCount++
    return file
  }

  // Report the path of the counted file, so that getVirtualFile keeps the cached file and counts one resolve only.
  override fun getPath(cachedFile: VirtualFile): String = path
}

/**
 * Gives the unversioned paths that a test declares, and counts how many times a processor asks for them.
 */
internal class TestChangeListManager : MockChangeListManager() {
  var unversionedPaths: List<FilePath> = emptyList()

  var unversionedRequestCount: Int = 0
    private set

  override fun getUnversionedFilesPaths(): List<FilePath> {
    unversionedRequestCount++
    return unversionedPaths
  }

  override fun isInUpdate(): Boolean = false

  override fun getStatus(file: VirtualFile): FileStatus = FileStatus.UNKNOWN
}

/**
 * Reports a path as potentially ignored only when a test adds the path to [ignoredPaths].
 */
internal class TestVcsIgnoreManager : VcsIgnoreManager {
  val ignoredPaths: MutableSet<String> = mutableSetOf()

  override fun isPotentiallyIgnoredFile(filePath: FilePath): Boolean = filePath.path in ignoredPaths

  override fun isPotentiallyIgnoredFile(file: VirtualFile): Boolean = file.path in ignoredPaths

  override fun isDirectoryVcsIgnored(dirPath: String): Boolean = false

  override fun isRunConfigurationVcsIgnored(configurationName: String): Boolean = false

  override fun removeRunConfigurationFromVcsIgnore(configurationName: String) = Unit
}
