// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Represents a virtual file specific to a tool window tab.
 * This is a holder of tool window representative information.
 * The class needs to be open to support changes in the editor title by overriding the `LightVirtualFileBase.getName` method.
 *
 * Note: This API is marked as experimental and internal, and its usage or behavior might change in future versions.
 *
 * @param editorTitle The name of the title of the editor.
 * @param icon The optional icon associated with the tab.
 * @param toolWindowId The ID of the tool window associated with this file.
 * @param component A UI component that was initially displayed in a tool window tab.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class ToolWindowTabFile(
  editorTitle: String,
  icon: Icon?,
  val toolWindowId: String,
  val component: JComponent
) : LightVirtualFile(editorTitle, ToolWindowTabFileType(icon), ""), OptionallyIncluded {

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

    override fun isMyFileType(file: VirtualFile) = file is ToolWindowTabFile
  }
}