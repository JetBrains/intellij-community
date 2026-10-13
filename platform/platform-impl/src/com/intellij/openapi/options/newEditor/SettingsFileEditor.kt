// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.impl.EditorAutoClosingHandler
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.Icon
import javax.swing.JComponent

/**
 * The file of the Settings editor tab. A project has one, see [SettingsToolWindowManager.file].
 *
 * The tab is not restored after a restart, and it is not in the navigation history.
 */
internal class SettingsVirtualFile : LightVirtualFile(CommonBundle.settingsTitle()), VirtualFileWithoutContent,
                                     IdeDocumentHistoryImpl.SkipFromDocumentHistory, EditorHistoryManager.OptionallyIncluded {
  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    putUserData(FileEditorManagerKeys.FORBID_PREVIEW_TAB, true)
  }

  override fun getPath(): String = name

  override fun isWritable(): Boolean = false

  override fun isIncludedInEditorHistory(project: Project): Boolean = false

  override fun isPersistedInEditorHistory(): Boolean = false
}

/**
 * Shows the page that the Settings tool window selects. The page belongs to [SettingsToolWindowManager], so a new tab shows the same page.
 */
internal class SettingsFileEditor(project: Project, private val file: SettingsVirtualFile) : UserDataHolderBase(), FileEditor {
  private val manager = SettingsToolWindowManager.getInstance(project)
  private val propertyChangeSupport = PropertyChangeSupport(this)
  private var isModified = manager.isModified

  override fun getComponent(): JComponent = manager.content

  override fun getPreferredFocusedComponent(): JComponent? = manager.getPreferredFocusedPageComponent()

  override fun getName(): String = CommonBundle.settingsTitle()

  override fun getFile(): VirtualFile = file

  override fun setState(state: FileEditorState) {
  }

  override fun isModified(): Boolean = isModified

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    propertyChangeSupport.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    propertyChangeSupport.removePropertyChangeListener(listener)
  }

  /** Updates the modification mark of the tab after a page gets or loses a change that waits for Apply. */
  fun updateModified() {
    val oldValue = isModified
    isModified = manager.isModified
    propertyChangeSupport.firePropertyChange(FileEditor.getPropModified(), oldValue, isModified)
  }

  override fun dispose() {
  }
}

internal class SettingsFileEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is SettingsVirtualFile

  override fun acceptRequiresReadAction(): Boolean = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = SettingsFileEditor(project, file as SettingsVirtualFile)

  override fun getEditorTypeId(): String = "settings-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

internal class SettingsFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    return if (file is SettingsVirtualFile) AllIcons.General.Settings else null
  }
}

/**
 * Keeps the Settings tab out of the tab limit. Then the tab closes no file when it opens, and an opened file never closes it.
 * With one tab allowed, the file that the user edited stays open behind the Settings tab.
 */
internal class SettingsTabAutoClosingHandler : EditorAutoClosingHandler {
  override fun isClosingAllowed(composite: EditorComposite): Boolean = composite.file !is SettingsVirtualFile
}

/**
 * Asks about the changes that wait for Apply before the Settings tab closes, so that Cancel keeps the tab open.
 * Other files of the same close still close.
 */
internal class SettingsTabPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean = canCloseFiles(listOf(file))

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean = filterFilesToClose(files).size == files.size

  override fun filterFilesToClose(files: Collection<VirtualFile>): Collection<VirtualFile> {
    return files.filter { it !is SettingsVirtualFile || SettingsToolWindowManager.findByFile(it)?.canCloseTab() != false }
  }
}
