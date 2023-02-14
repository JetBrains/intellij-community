// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.JComponent

/**
 * To open any of your JComponent descendant, call
 * <pre>{@code
 * JComponentEditorProvider.openEditor(project, "Title", jComponent)
 * }</pre>
 *
 * To customize Editor tab icon, you can, provide a custom fileType
 * <pre>{@code
 * val fileType = JComponentFileType()
 * JComponentEditorProvider.openEditor(project, "Title", jComponent, fileType)
 * }</pre>
 */
class JComponentEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val fileEditor = file.getUserData(EDITOR_KEY)
    return if (fileEditor != null) {
      fileEditor
    }
    else {
      val component = file.getUserData(JCOMPONENT_KEY) ?: error("JCOMPONENT_KEY key is null while creating JComponentFileEditor.")
      val newEditor = JComponentFileEditor(file, component)
      file.putUserData(EDITOR_KEY, newEditor)
      newEditor
    }
  }

  override fun accept(project: Project, file: VirtualFile) = isJComponentFile(file)

  override fun getEditorTypeId() = "jcomponent-editor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val JCOMPONENT_KEY: Key<JComponent> = Key.create("jcomponent.editor.jcomponent")
    private val EDITOR_KEY: Key<FileEditor> = Key.create("jcomponent.editor.fileeditor")

    fun openEditor(project: Project, @NlsContexts.DialogTitle title: String, component: JComponent,
                   fileType: FileType = JComponentFileType.INSTANCE): Array<FileEditor> {
      val file = LightVirtualFile(title, fileType, "")
      file.putUserData(JCOMPONENT_KEY, component)
      return FileEditorManager.getInstance(project).openFile(file, true)
    }

    @JvmStatic
    fun isJComponentFile(file: VirtualFile): Boolean = file.getUserData(JCOMPONENT_KEY) != null
  }
}
