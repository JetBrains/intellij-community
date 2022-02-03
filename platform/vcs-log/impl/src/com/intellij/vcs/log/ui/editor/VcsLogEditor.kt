// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogEditorUtil.disposeLogUis
import com.intellij.vcs.log.ui.VcsLogPanel
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class VcsLogFileType private constructor() : FileType {
  override fun getName(): String = "VcsLog"
  override fun getDescription(): String = VcsLogBundle.message("filetype.vcs.log.description")
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon = AllIcons.Vcs.Branch
  override fun isBinary(): Boolean = true
  override fun isReadOnly(): Boolean = true

  companion object {
    val INSTANCE = VcsLogFileType()
  }
}

abstract class VcsLogFile(name: String) : LightVirtualFile(name, VcsLogFileType.INSTANCE, "") {
  init {
    isWritable = false
  }

  abstract fun createMainComponent(project: Project): JComponent
}

class VcsLogIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? = (file as? VcsLogFile)?.fileType?.icon
}

class VcsLogEditor(private val project: Project, private val vcsLogFile: VcsLogFile) : FileEditorBase() {
  internal val rootComponent: JComponent = JPanel(BorderLayout()).also {
    it.add(vcsLogFile.createMainComponent(project), BorderLayout.CENTER)
  }

  override fun getComponent(): JComponent = rootComponent
  override fun getPreferredFocusedComponent(): JComponent? = VcsLogPanel.getLogUis(component).firstOrNull()?.mainComponent
  override fun getName(): String = VcsLogBundle.message("vcs.log.editor.name")
  override fun getFile() = vcsLogFile
}

class VcsLogEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is VcsLogFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return VcsLogEditor(project, file as VcsLogFile)
  }

  override fun getEditorTypeId(): String = "VcsLogEditor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun disposeEditor(editor: FileEditor) {
    editor.disposeLogUis()

    super.disposeEditor(editor)
  }
}
