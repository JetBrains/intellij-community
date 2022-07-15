// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

fun getForegroundColorForFile(project: Project, file: VirtualFile): ColorKey? {
  val first: EditorTabColorProvider = EditorTabColorProvider.EP_NAME.extensionList.first {
    val editorTabColor = it.getEditorTabForegroundColor(project, file)
    return@first editorTabColor != null
  }
  return first.getEditorTabForegroundColor(project, file)
}

