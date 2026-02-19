// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.project.Project
import java.awt.datatransfer.Transferable
import java.io.File

/**
 * Handles drag-and-drop events containing files.
 */
interface FileDropHandler {
  /**
   * @return true, if the event is handled and must not be propagated further to the rest of handlers
   */
  suspend fun handleDrop(e: FileDropEvent): Boolean
}

class FileDropEvent(
  val project: Project,
  val transferable: Transferable,
  val files: Collection<File>,
  val editor: Editor?
) {
  override fun toString(): String {
    return "FileDropEvent(project=$project, transferable=$transferable, files=$files, editor=$editor)"
  }
}