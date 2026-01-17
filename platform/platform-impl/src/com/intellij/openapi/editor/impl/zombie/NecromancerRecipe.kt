// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile


open class Recipe(
  val project: Project,
  val fileId: Int,
  val file: VirtualFile,
  val document: Document,
)

class TurningRecipe(
  project: Project,
  fileId: Int,
  file: VirtualFile,
  document: Document,
  val documentModStamp: Long,
  val editor: Editor,
) : Recipe(project, fileId, file, document) {
  fun isValid(): Boolean {
    return documentModStamp == document.modificationStamp
  }
}

class SpawnRecipe(
  project: Project,
  fileId: Int,
  file: VirtualFile,
  document: Document,
  val documentModStamp: Long,
  val editorSupplier: suspend () -> EditorEx,
  val highlighterReady: suspend () -> Unit,
) : Recipe(project, fileId, file, document) {
  fun isValid(editor: Editor): Boolean {
    return isValid() && !editor.isDisposed
  }

  fun isValid(): Boolean {
    return !project.isDisposed && documentModStamp == document.modificationStamp
  }
}
