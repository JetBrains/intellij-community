// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon
import javax.swing.JComponent

internal class ToolWindowTabFileImpl(
  fileName: String,
  icon: Icon?,
  val component: JComponent
) : LightVirtualFile(fileName, ToolWindowTabFileType(icon), ""), OptionallyIncluded {

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    isWritable = true
  }

  override fun isIncludedInEditorHistory(project: Project): Boolean = true
  override fun isPersistedInEditorHistory(): Boolean = false

  override fun setWritable(writable: Boolean) {
  }

  private class ToolWindowTabFileType(val myIcon: Icon?) : FakeFileType() {

    override fun getName() = "ToolWindowTab"

    @NlsSafe
    override fun getDescription() = "$name Fake File Type"

    override fun getIcon() = myIcon

    override fun isMyFileType(file: VirtualFile) = file is ToolWindowTabFileImpl
  }
}