// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Contains TODO items and file metadata for display and grouping in split mode.
 *
 * @property fileId The virtual file ID.
 * @property name The file name.
 * @property presentableUrl The file path for display.
 * @property moduleName The name of the module that contains the file, or `null` if no module contains it.
 * @property packageName The package name, an empty string for the default package, or `null` for directory grouping.
 * @property todos The TODO items in the file.
 * @property packageRootName The package prefix of the source root.
 * @property directoryPath The ancestors from the file's immediate parent to the grouping root, inclusive.
 * @property fileStatusColor The color that represents file's VCS status (added, ignored, modified, etc.).
 */
@ApiStatus.Internal
@Serializable
data class TodoFileResult(
  val fileId: VirtualFileId,
  val name: String,
  val presentableUrl: String,
  val moduleName: String?,
  val packageName: String?,
  val todos: List<TodoResult>,
  val packageRootName: String? = null,
  val directoryPath: List<TodoDirectoryResult> = emptyList(),
  val fileStatusColor: ColorId? = null,
)
