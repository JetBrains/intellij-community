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
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.VcsLogUiEx
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

val VCS_LOG_FILE_DISPLAY_NAME_GENERATOR = Key.create<(List<VcsLogUiEx>) -> String>("VCS_LOG_FILE_GENERATE_DISPLAY_NAME")

class VcsLogFile(
  internal val rootComponent: JComponent,
  internal val logUis: List<VcsLogUiEx>,
  name: String
) : LightVirtualFile(name, VcsLogFileType.INSTANCE, "") {
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

  init {
    container.add(file.rootComponent, BorderLayout.CENTER)
  }

  fun beforeEditorClose(disposeLogUis: Boolean) {
    val logUis = vcsLogFile?.logUis

    container.removeAll()
    vcsLogFile = null

    if (disposeLogUis) {
      logUis?.forEach(Disposer::dispose)
    }
  }

  override fun getComponent(): JComponent = container
  override fun getPreferredFocusedComponent(): JComponent? = vcsLogFile?.logUis?.firstOrNull()?.mainComponent
  override fun getName(): String = "Vcs Log Editor"
}

class VcsLogEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is VcsLogFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return VcsLogEditor(file as VcsLogFile)
  }

  override fun getEditorTypeId(): String = "VcsLogEditor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun disposeEditor(editor: FileEditor) {
    val file = editor.file
    val closingToReopen = file != null && file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) == true
    (editor as VcsLogEditor).beforeEditorClose(!closingToReopen)

    super.disposeEditor(editor)
  }
}

class VcsLogEditorTabTitleProvider : EditorTabTitleProvider {

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (file !is VcsLogFile) return null
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is VcsLogFile) return null
    return file.getUserData(VCS_LOG_FILE_DISPLAY_NAME_GENERATOR)
             ?.let { displayNameGenerator -> (file as? VcsLogFile)?.logUis?.run(displayNameGenerator) }
           ?: file.name
  }
}