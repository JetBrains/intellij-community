// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogTabsManager
import com.intellij.vcs.log.ui.VcsLogPanel
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class VcsLogFileType : FileType {
  override fun getName(): String = "VcsLog"
  override fun getDescription(): String = VcsLogBundle.message("vcs.log.file.type.description")
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon? = AllIcons.Vcs.Branch
  override fun isBinary(): Boolean = true
  override fun isReadOnly(): Boolean = true
  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

  companion object {
    val INSTANCE = VcsLogFileType()
  }
}

class VcsLogFile(internal val vcsLogPanel: VcsLogPanel, name: String) : LightVirtualFile(name, VcsLogFileType.INSTANCE, "") {
  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }
}

class VcsLogIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? = (file as? VcsLogFile)?.fileType?.icon
}

class VcsLogEditor(file: VcsLogFile) : FileEditorBase() {
  private val container: JComponent = JPanel(BorderLayout())

  private var vcsLogFile: VcsLogFile? = file
  private val vcsLogPanel: VcsLogPanel?
    get() = vcsLogFile?.vcsLogPanel

  init {
    container.add(file.vcsLogPanel, BorderLayout.CENTER)
    Disposer.register(this, file.vcsLogPanel.getUi())
  }

  fun beforeEditorClose() {
    val ui = vcsLogPanel?.getUi()

    container.removeAll()
    vcsLogFile = null

    if (ui != null) {
      Disposer.dispose(ui)
    }
  }

  override fun getComponent(): JComponent = container
  override fun getPreferredFocusedComponent(): JComponent? = vcsLogPanel
  override fun getName(): String = "Vcs Log Editor"
  override fun getFile(): VirtualFile? = vcsLogFile
}

class VcsLogEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is VcsLogFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return VcsLogEditor(file as VcsLogFile)
  }

  override fun getEditorTypeId(): String = "VcsLogEditor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class VcsLogEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    return (file as? VcsLogFile)?.vcsLogPanel?.getUi()?.let { VcsLogTabsManager.generateDisplayName(it) }
  }
}