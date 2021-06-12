// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions.related

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabBaseAction
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabDataProvider
import com.intellij.ui.Gray
import com.intellij.ui.TextIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil

class RelatedFilesTabAction(val index: Int,  provider: EditorTabDataProvider<FileEditorProvider>) : EditorTabBaseAction<FileEditorProvider>(provider){
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      e.getData(PlatformDataKeys.VIRTUAL_FILE)?.let { file ->
        val list = getList(e)
        val editor = list[index]
        FileEditorManager.getInstance(project)?.setSelectedEditor(file, editor.editorTypeId)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val list = getList(e)
    e.presentation.isEnabledAndVisible = if (list.isEmpty() || index >= list.size || list.size < 2) {
      false
    }
    else {
      e.project?.let { project ->
        val editorManager = FileEditorManager.getInstance(project)
        e.getData(PlatformDataKeys.VIRTUAL_FILE)?.let { file ->

          e.presentation.icon = editorManager?.getEditors(file)?.getOrNull(index)?.let { editor ->
             TextIcon(". ${editor.name} .", UIUtil.getLabelForeground(), Gray.TRANSPARENT, 0).apply {
                 val font = JBFont.label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI) - 1)
                 setFont(if(editor == editorManager.getSelectedEditor(file)) font.asBold() else font)
            }
          }
        }
      }

      true
    }

    super.update(e)
  }
}