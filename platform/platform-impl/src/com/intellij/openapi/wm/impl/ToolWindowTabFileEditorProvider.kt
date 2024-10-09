// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ToolWindowTabFileEditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean = file is ToolWindowTabFileImpl

  override fun acceptRequiresReadAction(): Boolean = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    ToolWindowTabFileEditor(project, file as ToolWindowTabFileImpl)

  override fun getEditorTypeId(): String = "ToolWindowTabFileEditor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}