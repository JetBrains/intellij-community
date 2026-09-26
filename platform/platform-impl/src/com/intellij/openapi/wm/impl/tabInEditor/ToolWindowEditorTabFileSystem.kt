// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.Base64

/**
 * Non-physical VFS used to represent persistent tool window editor tabs as [VirtualFile] instances.
 */
internal class ToolWindowEditorTabFileSystem : DeprecatedVirtualFileSystem(),
                                               NonPhysicalFileSystem {

  override fun getProtocol(): String = TOOL_WINDOW_EDITOR_TAB_VFS_PROTOCOL

  override fun refresh(asynchronous: Boolean) {}

  override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

  /**
   * Searches for a persistent tool window editor tab file specified by [path].
   *
   * The path uniquely identifies a file within this virtual file system and is expected to have the
   * format defined by [PersistentToolWindowEditorTabPath].
   *
   * Resolving the file restores only the [ToolWindowEditorTabFile] identity. It does not restore or
   * attach the corresponding tool window content.
   *
   * The same path may be resolved multiple times by different code parts.
   * For the same path, this method must return the same [VirtualFile] instance, or an
   * equal instance according to the [VirtualFile] contract.
   * For example, during project loading the same path may be resolved independently
   * while restoring editor tabs in [com.intellij.openapi.fileEditor.impl.EditorsSplitters]
   * and editor history in [com.intellij.openapi.fileEditor.impl.EditorHistoryManager].
   *
   * @param path the persistent VFS path identifying the editor tab
   * @return the corresponding virtual file, or `null` if tool window editor tabs are disabled or
   * the path cannot be parsed
   */
  override fun findFileByPath(path: String): VirtualFile? {
    if (!ToolWindowEditorTabSupportUtil.isEnabled()) {
      return null
    }

    val tabPath = PersistentToolWindowEditorTabPath.parse(path) ?: return null
    return ToolWindowEditorTabFileRegistry.getInstance().getOrCreatePersistentFile(tabPath)
  }

  /**
   * Returns a user-friendly URL for a tool window editor tab.
   *
   * The VFS path contains implementation details and is not meaningful to the user. If the path
   * resolves to an existing tab file, its presentable path is returned instead. This keeps UI places
   * that use [VirtualFile.getPresentableUrl] consistent with the tab's current presentable name.
   *
   * Falls back to [path] if the path cannot be parsed, the file is not currently known, or its
   * presentable path is empty.
   */
  override fun extractPresentableUrl(path: String): String {
    val tabPath = PersistentToolWindowEditorTabPath.parse(path) ?: return path
    val filePresentablePath = ToolWindowEditorTabFileRegistry.getInstance()
      .findFile(tabPath)
      ?.presentablePath
      ?.takeIf { it.isNotEmpty() }

    return filePresentablePath ?: path
  }
}

/**
 * Persistent VFS path identifying a tool window editor tab across IDE sessions.
 *
 * Paths have the following structure:
 *
 * `projectLocationHash/toolWindowId/persistenceId/name`
 *
 * [projectLocationHash] associates the tab with its project, [toolWindowId] identifies the owning
 * tool window, and [persistenceId] distinguishes multiple persistent tabs belonging to the same
 * tool window. [name] stores the tab's last known presentable name so it is available after the
 * project is reopened, before the restored tab's [ToolWindowEditorTabSession] is loaded.
 */
internal class PersistentToolWindowEditorTabPath(
  val projectLocationHash: String,
  val toolWindowId: String,
  val persistenceId: String,
  @NlsSafe val name: String = "",
) {
  /**
   * Returns a path with the same identity and [name] as its title.
   */
  fun withName(@NlsSafe name: String): PersistentToolWindowEditorTabPath =
    PersistentToolWindowEditorTabPath(
      projectLocationHash = projectLocationHash,
      toolWindowId = toolWindowId,
      persistenceId = persistenceId,
      name = name,
    )

  override fun toString(): String {
    return listOf(projectLocationHash, toolWindowId, persistenceId, name)
      .joinToString("/") { encode(it) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    val otherPath = other as? PersistentToolWindowEditorTabPath ?: return false

    return projectLocationHash == otherPath.projectLocationHash &&
           toolWindowId == otherPath.toolWindowId &&
           persistenceId == otherPath.persistenceId
  }

  override fun hashCode(): Int {
    var result = projectLocationHash.hashCode()
    result = 31 * result + toolWindowId.hashCode()
    result = 31 * result + persistenceId.hashCode()
    return result
  }

  companion object {
    fun parse(path: String): PersistentToolWindowEditorTabPath? {
      val segments = path.split('/')
      if (segments.size != 4) return null

      return runCatching {
        PersistentToolWindowEditorTabPath(
          projectLocationHash = decode(segments[0]),
          toolWindowId = decode(segments[1]),
          persistenceId = decode(segments[2]),
          name = decode(segments[3])
        )
      }.getOrNull()
    }

    private fun encode(value: String): String =
      Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
      Base64.getUrlDecoder()
        .decode(value)
        .toString(Charsets.UTF_8)
  }
}

private const val TOOL_WINDOW_EDITOR_TAB_VFS_PROTOCOL: String = "tool-window-editor-tab"

internal fun getToolWindowEditorTabFileSystem(): ToolWindowEditorTabFileSystem =
  VirtualFileManager.getInstance()
    .getFileSystem(TOOL_WINDOW_EDITOR_TAB_VFS_PROTOCOL) as ToolWindowEditorTabFileSystem