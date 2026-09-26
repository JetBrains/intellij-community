// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level registry of persistent [ToolWindowEditorTabFile] instances, keyed by their
 * [PersistentToolWindowEditorTabPath].
 *
 * ### Why the [ToolWindowEditorTabFile] instances are registered in the map
 *
 * [com.intellij.openapi.vfs.VirtualFile] permits multiple instances representing the same file, but
 * requires them to be equal, have the same `hashCode`, and use shared storage for all related data,
 * including user data.
 * [ToolWindowEditorTabFile] does not provide such shared identity semantics on its own.
 *
 * The registry keeps one [ToolWindowEditorTabFile] instance per persistent path and
 * returns that instance whenever the path is resolved again.
 *
 * This is important because the same persisted path may be resolved multiple times.
 *
 * ### Why application level
 *
 * A persistent path must be resolvable before its project is fully opened, while project services
 * may still be unavailable. For this reason, file identity cannot be owned by
 * [ToolWindowEditorTabManager].
 *
 * ### Lifetime
 *
 * The registry holds strong references to the files. Entries are removed explicitly by [removeFile]
 * when a tab is closed and by [removeFilesForProject] when its project is closed; see
 * [ToolWindowEditorTabFileRegistryCleaner].
 */
@Service(Service.Level.APP)
internal class ToolWindowEditorTabFileRegistry {
  /**
   * Maps persistent tab paths to their virtual file instances.
   *
   * Keeping the files here prevents the VFS from creating a new [ToolWindowEditorTabFile] every time
   * the same persistent path is resolved.
   */
  private val fileByPath = ConcurrentHashMap<PersistentToolWindowEditorTabPath, ToolWindowEditorTabFile>()

  /**
   * Returns the valid file associated with [path], creating one if no such file exists.
   */
  fun getOrCreatePersistentFile(path: PersistentToolWindowEditorTabPath): ToolWindowEditorTabFile? {
    if (ToolWindowEditorTabPersistenceProviderUtil.getProvider(path.toolWindowId) == null) {
      return null
    }

    return fileByPath.compute(path) { _, existingFile ->
      if (existingFile != null && existingFile.isValid) {
        existingFile
      }
      else {
        // Add a new mapping if no file is registered, or replace the mapping for an invalidated file.
        ToolWindowEditorTabFile(
          toolWindowId = path.toolWindowId,
          persistentPath = path,
        )
      }
    }!!
  }

  /**
   * Returns the currently registered valid file for [path], or `null` if no valid file exists.
   */
  fun findFile(path: PersistentToolWindowEditorTabPath): ToolWindowEditorTabFile? {
    val file = fileByPath[path]
    return file?.takeIf { it.isValid }
  }

  /**
   * Removes [file] from the registry if it is currently registered for its persistent path.
   */
  fun removeFile(file: ToolWindowEditorTabFile) {
    val persistentPath = file.persistentPath ?: return
    fileByPath.remove(persistentPath, file)
  }

  /**
   * Removes all registered files that belong to the project identified by [projectLocationHash].
   */
  fun removeFilesForProject(projectLocationHash: String) {
    fileByPath.keys.removeIf { path ->
      path.projectLocationHash == projectLocationHash
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowEditorTabFileRegistry = service()
  }
}

/**
 * Removes persistent tool window editor tab files associated with a project when the project is closed.
 *
 * The files are stored in the application-level [ToolWindowEditorTabFileRegistry], so they must be
 * explicitly removed when their owning project is no longer open.
 */
internal class ToolWindowEditorTabFileRegistryCleaner : ProjectCloseListener {
  override fun projectClosed(project: Project) {
    ToolWindowEditorTabFileRegistry.getInstance().removeFilesForProject(project.locationHash)
  }
}
