// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Virtual file that represents tool window content opened in an editor tab.
 *
 * The file provides an identity for the editor tab and does not own its
 * project-specific runtime or UI state. The attached [com.intellij.ui.content.Content], editor
 * components, presentation, project, and other runtime state are stored in
 * [ToolWindowEditorTabSession], which is owned by [ToolWindowEditorTabManager].
 *
 * A file may exist without an associated session. This happens during restoration, when a
 * persistent VFS path must be resolved before the corresponding project and tool window content are
 * available. Once the content is restored or moved into the editor, [ToolWindowEditorTabManager]
 * creates a session for this file.
 *
 * Persistent files are identified by [persistentPath] and may be restored across IDE sessions.
 * Files with no persistent path are transient and exist only for the lifetime of the corresponding
 * editor tab.
 *
 * @param toolWindowId the ID of the tool window that owns the represented content
 * @param persistentPath the persistent identity of the editor tab, or `null` for a transient tab
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ToolWindowEditorTabFile internal constructor(
  val toolWindowId: String,
  persistentPath: PersistentToolWindowEditorTabPath?,
) : LightVirtualFile(
  "",
  ToolWindowEditorTabFileType,
  "",
), OptionallyIncluded, VirtualFilePathWrapper {

  @Volatile
  internal var persistentPath: PersistentToolWindowEditorTabPath? = persistentPath
    private set

  /**
   * The last known user-visible name of the tab.
   *
   * It is kept separately from [persistentPath] so transient files also have a meaningful
   * [presentablePath][getPresentablePath].
   */
  @NlsSafe
  internal var presentableName: String = persistentPath?.name ?: ""
    private set

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  /**
   * Returns the serialized persistent path for a persistent tab.
   *
   * Transient tabs have no persistent VFS identity and use the path provided by
   * [com.intellij.testFramework.LightVirtualFileBase].
   */
  override fun getPath(): String = persistentPath?.toString() ?: super.getPath()

  /**
   * Returns the persistent tool window tab file system for persistent tabs and the default
   * [com.intellij.testFramework.LightVirtualFileBase] file system for transient tabs.
   */
  override fun getFileSystem(): VirtualFileSystem {
    return if (persistentPath != null) {
      getToolWindowEditorTabFileSystem()
    }
    else {
      super.getFileSystem()
    }
  }

  /**
   * Returns the last known user-visible name of this tab.
   *
   * This is used instead of the VFS path in UI places where the serialized path would expose
   * implementation details. For persistent tabs, the initial value comes from the stored path so
   * the name is available even before the corresponding tool window content is restored.
   */
  override fun getPresentablePath(): String = presentableName

  override fun enforcePresentableName(): Boolean = true

  override fun isIncludedInEditorHistory(project: Project): Boolean =
    persistentPath == null || persistentPath!!.projectLocationHash == project.locationHash

  override fun isPersistedInEditorHistory(): Boolean = persistentPath != null

  /**
   * Keeps tool window editor tab files read-only.
   */
  override fun setWritable(writable: Boolean) {
    if (writable) throw UnsupportedOperationException()
    super.setWritable(false)
  }

  /**
   * Marks this file as invalid when the corresponding editor tab is closed.
   * Otherwise, it may remain visible in places such as Recent Files.
   */
  internal fun invalidate() {
    isValid = false
  }

  /**
   * Updates the last known user-visible name of this tab.
   *
   * For persistent tabs, the name is also stored in [persistentPath] so it is available when the
   * project is reopened, after the tab is restored but before its [ToolWindowEditorTabSession] is loaded.
   */
  internal fun updatePresentableName(@NlsSafe name: String) {
    if (presentableName == name) return

    presentableName = name
    persistentPath?.let { persistentPath = it.withName(name) }
  }
}
