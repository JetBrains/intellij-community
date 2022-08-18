// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.LightVirtualFileBase

fun isInsideMainEditor(dataContext: DataContext): Boolean {
  val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return false
  return !DiffUtil.isDiffEditor(editor) && editor.isFileEditor()
}

private fun Editor.isFileEditor(): Boolean {
  val documentManager = FileDocumentManager.getInstance()
  val virtualFile = documentManager.getFile(document)
  if (virtualFile is LightVirtualFileBase) return false
  return virtualFile != null && virtualFile.isValid
}
