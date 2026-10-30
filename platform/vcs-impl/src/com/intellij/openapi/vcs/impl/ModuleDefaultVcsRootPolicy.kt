// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.project.stateStore
import com.intellij.workspaceModel.ide.ProjectRootEntity
import java.nio.file.Path

/**
 * The default VCS roots of a project: the base directory, the `.idea` directory, and every module content root.
 * When the registry key [PROJECT_ROOTS_REGISTRY_KEY] is on, the project roots replace the base directory. A change
 * of the key applies after a restart. A root that more than one source supplies stays a default root until every
 * source drops it.
 */
internal class ModuleDefaultVcsRootPolicy(project: Project) : DefaultVcsRootPolicy(project) {
  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, MyRootsListener())
  }

  override fun getDefaultVcsRoots(): Collection<VirtualFile> {
    val projectRootsReplaceBaseDirectory = shouldReplaceBaseDirectoryWithProjectRoots()
    val result = LinkedHashSet<VirtualFile>()
    if (!projectRootsReplaceBaseDirectory) result += findBaseDirectories()
    result += runReadActionBlocking {
      collectWorkspaceRoots(WorkspaceModel.getInstance(myProject).currentSnapshot, withProjectRoots = projectRootsReplaceBaseDirectory)
    }
    return result
  }

  /** Tells whether the project roots replace the base directory, see [PROJECT_ROOTS_REGISTRY_KEY]. */
  private fun shouldReplaceBaseDirectoryWithProjectRoots(): Boolean = Registry.`is`(PROJECT_ROOTS_REGISTRY_KEY)

  /** The base directory and its `.idea` directory, when the VFS knows them. */
  private fun findBaseDirectories(): List<VirtualFile> {
    val fileManager = VirtualFileManager.getInstance()
    val baseDir = myProject.basePath?.let { fileManager.findFileByNioPath(Path.of(it)) } ?: return emptyList()
    val directoryStorePath = myProject.stateStore.directoryStorePath ?: return listOf(baseDir)
    val ideaDir = fileManager.findFileByNioPath(directoryStorePath) ?: return listOf(baseDir)
    return listOf(baseDir, ideaDir)
  }

  /** Every module content root, and every project root when [withProjectRoots]. Local directories only. */
  private fun collectWorkspaceRoots(storage: ImmutableEntityStorage, withProjectRoots: Boolean): Set<VirtualFile> {
    val contentRoots = storage.entities(ContentRootEntity::class.java).map { it.url }
    val projectRoots = if (withProjectRoots) storage.entities(ProjectRootEntity::class.java).map { it.root } else emptySequence()
    return (contentRoots + projectRoots)
      .mapNotNull { it.virtualFile }
      .filterTo(LinkedHashSet()) { it.isInLocalFileSystem && it.isDirectory }
  }

  private inner class MyRootsListener : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
      if (!event.changesRootUrls()) return
      val withProjectRoots = shouldReplaceBaseDirectoryWithProjectRoots()
      val before = collectWorkspaceRoots(event.storageBefore, withProjectRoots)
      val after = collectWorkspaceRoots(event.storageAfter, withProjectRoots)
      val removed = before - after
      val added = after - before
      if (removed.isNotEmpty() || added.isNotEmpty()) {
        notifyDefaultRootsChanged(removed, added)
      }
    }
  }
}

/** Tells whether the change adds or removes a content root URL or a project root URL. A modification keeps the URL. */
private fun VersionedStorageChange.changesRootUrls(): Boolean =
  getChanges(ContentRootEntity::class.java).any { it.oldEntity?.url != it.newEntity?.url } ||
  getChanges(ProjectRootEntity::class.java).any { it.oldEntity?.root != it.newEntity?.root }
