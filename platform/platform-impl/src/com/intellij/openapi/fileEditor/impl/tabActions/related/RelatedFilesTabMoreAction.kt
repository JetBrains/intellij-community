// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions.related

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabBaseAction
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabDataProvider

class RelatedFilesTabMoreAction(val maxCount: Int,  provider: EditorTabDataProvider<FileEditorProvider>) : EditorTabBaseAction<FileEditorProvider>(provider){
  override fun actionPerformed(e: AnActionEvent) {
    //TODO("Not yet implemented")
  }

  override fun update(e: AnActionEvent) {
    val list = getList(e)
    e.presentation.isEnabledAndVisible = if (list.isEmpty() || list.size <= maxCount) {
      false
    }
    else {
      true
    }
  }
}