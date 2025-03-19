// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.stacktrace.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore.loadText
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/**
 * Java stacktrace weighed file editor provider.
 */
class StackTraceFileEditorProvider : WeighedFileEditorProvider() {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.extension == "txt" && isThreadDump(loadText(file))
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = StackTraceFileEditor(project, file)

  override fun getEditorTypeId(): String = "stacktrace-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR


  private companion object {
    private fun isThreadDump(text: String): Boolean {
      val stackTracePattern = Pattern.compile(
        """\t*at [_\w/+.]+[_\w$0-9/]+\.[_\w/]+\([_\w/]+\.(java|kt):\d+\)+[ ~*\[\w.:/\]]*"""
      )
      val threadStateNames = Thread.State.entries.map { it.name }.toSet()
      val matcher = stackTracePattern.matcher("")

      var consecutiveStackLines = 0
      var hasThreadState = false

      for (line in text.split("\n")) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue
        if (trimmedLine.startsWith("java.lang.Thread.State:")) {
          hasThreadState = threadStateNames.any { state -> trimmedLine.contains(state) }
        }
        if (matcher.reset(trimmedLine).matches()) {
          consecutiveStackLines++
        } else {
          consecutiveStackLines = 0
        }
        if (consecutiveStackLines > 2 && hasThreadState) return true
      }
      return false
    }
  }
}
