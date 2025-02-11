// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileEditor.impl.tabActions.CloseTab
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.impl.FileStatusProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.GotItTooltip
import com.intellij.ui.UIBundle
import com.intellij.ui.tabs.impl.TabLabel
import java.awt.Point

class SettingsFileEditorProvider : FileEditorProvider, FileStatusProvider, EditorTabTitleProvider, DumbAware {
  companion object {
    const val ID = "SettingsFileEditor"
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is SettingsVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val settingsFile = file as SettingsVirtualFile
    val dialog = settingsFile.getOrCreateDialog()
    val tree = (dialog.editor as? SettingsEditor)?.treeView?.tree
    installCloseGotItHookIfNecessary(project, dialog.disposable)
    return SettingsFileEditor(settingsFile, dialog.rootPane, tree, dialog.disposable)
  }

  private fun installCloseGotItHookIfNecessary(project: Project, parentDisposable: Disposable) {
    val gotItTooltip = GotItTooltip("close.non.modal.settings",
                   UIBundle.message("settings.tab.close.gotit.text"),
                   parentDisposable = parentDisposable).withTimeout()
    if (!gotItTooltip.canShow()) {
      Disposer.dispose(gotItTooltip)
      return
    }
    Disposer.register(gotItTooltip) { gotItTooltip.gotIt() }
    val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
    val curWin = fileEditorManager.currentWindow ?: return
    val currentTabLabelComponent: TabLabel = curWin.tabbedPane.editorTabs.selectedLabel ?: return
    val info = currentTabLabelComponent.info
    val children = (info.tabLabelActions as? DefaultActionGroup)?.childActionsOrStubs?.map { action ->
      if (action !is CloseTab) {
        action
      }
      else {
        GotItAwareCloseTab(action, currentTabLabelComponent, gotItTooltip)
      }
    } ?: return
    val updatedGroup = DefaultActionGroup(children)
    info.setTabLabelActions(updatedGroup, ActionPlaces.EDITOR_TAB)
  }

  override fun getEditorTypeId(): String {
    return ID
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_OTHER_EDITORS
  }

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor as SettingsFileEditor)
  }

  override fun getFileStatus(virtualFile: VirtualFile): FileStatus? {
    val isModified = (virtualFile as? SettingsVirtualFile)?.isModified() ?: return null
    return if (isModified)
      FileStatus.MODIFIED
    else
      FileStatus.NOT_CHANGED
  }

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @NlsContexts.Tooltip String? {
    if (virtualFile !is SettingsVirtualFile)
      return super.getEditorTabTooltipText(project, virtualFile)
    return CommonBundle.settingsTitle()
  }

  override fun getEditorTabTitle(project: Project, virtualFile: VirtualFile): @NlsContexts.TabTitle String? {
    if (virtualFile !is SettingsVirtualFile)
      return null
    return CommonBundle.settingsTitle()
  }

  private class GotItAwareCloseTab(
    private val closeTabAction: CloseTab,
    private val currentTabLabelComponent: TabLabel,
    private val gotItTooltip: GotItTooltip,
  ) : AnAction(), DumbAware {
    init {
      copyFrom(closeTabAction)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return closeTabAction.actionUpdateThread
    }

    override fun update(e: AnActionEvent) {
      closeTabAction.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      if (!gotItTooltip.canShow()) {
        return closeTabAction.actionPerformed(e)
      }
      gotItTooltip
        .withHeader(UIBundle.message("settings.tab.close.gotit.header"))
        .withPosition(Balloon.Position.below)
        .show(currentTabLabelComponent) { component, baloon ->
          val dimension = component.size
          Point(dimension.width - 16, dimension.height - 12)
        }
      gotItTooltip.gotIt()
    }

  }
}
