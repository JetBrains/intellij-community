// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions.related

import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabAction
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabActionFactory
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabBaseAction
import com.intellij.openapi.fileEditor.impl.tabActions.base.EditorTabDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Suppress("ComponentNotRegistered")
class RelatedFilesTabActionGroup : EditorTabAction<FileEditorProvider>(2, RelatedFilesTabActionFactory())
private class RelatedFilesTabActionFactory : EditorTabActionFactory<FileEditorProvider> {
  private val provider = RelatedFilesTabDataProvider()

  override fun createTabAction(i: Int): EditorTabBaseAction<FileEditorProvider> {
    return RelatedFilesTabAction(i, provider)
  }

  override fun createTabMoreAction(max: Int): EditorTabBaseAction<FileEditorProvider> {
    return RelatedFilesTabMoreAction(max, provider)
  }
}

class RelatedFilesTabDataProvider: EditorTabDataProvider<FileEditorProvider> {
  override fun getList(project: Project, file: VirtualFile): List<FileEditorProvider> {
    return FileEditorProviderManager.getInstance().getProviders(project, file).asList()
  }
}