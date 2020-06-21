// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.impl

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.TerminalShellCommandHandler
import java.io.File

class OpenFileShellCommandHandler : TerminalShellCommandHandler {
  override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String) =
    handleCommand(command, localSession, workingDirectory) { file -> checkRegisteredFileType(file) }

  override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String) =
    handleCommand(command, localSession, workingDirectory) { file -> openFileEditor(project, file) }

  private fun checkRegisteredFileType(file: VirtualFile?) =
    file != null && FileTypeRegistry.getInstance().getFileTypeByFile(file) != UnknownFileType.INSTANCE

  private fun openFileEditor(project: Project, file: VirtualFile?) =
    file != null && FileEditorManager.getInstance(project).openFile(file, true).isNotEmpty()

  private fun handleCommand(command: String, localSession: Boolean, workingDirectory: String?, block: (VirtualFile?) -> Boolean): Boolean {
    val prefix = "open "
    if (!command.startsWith(prefix)) return false

    var path = command.substring(prefix.length)
    if (!localSession) return false

    path = path.trim()

    val file = LocalFileSystem.getInstance().findFileByIoFile(File(path))
    if (file != null && !file.isDirectory && file.exists()) return block.invoke(file)

    if (workingDirectory != null) return LocalFileSystem.getInstance().findFileByIoFile(
      File(workingDirectory, path))?.takeIf { it.exists() && !it.isDirectory }.let { block.invoke(it) }

    return false
  }
}