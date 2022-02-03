// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.editor

import com.intellij.ide.actions.SplitAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.util.VcsLogUtil
import java.awt.BorderLayout
import javax.swing.JComponent

internal class DefaultVcsLogFile(private val pathId: VcsLogVirtualFileSystem.VcsLogComplexPath,
                                 private var filters: VcsLogFilterCollection? = null) :
  VcsLogFile(VcsLogTabsManager.getFullName(pathId.logId)), VirtualFilePathWrapper { //NON-NLS not displayed

  private val fileSystemInstance: VcsLogVirtualFileSystem = VcsLogVirtualFileSystem.getInstance()
  internal val tabId get() = pathId.logId

  internal var tabName: String
    get() = service<VcsLogEditorTabNameCache>().getTabName(path) ?: name
    set(value) = service<VcsLogEditorTabNameCache>().putTabName(path, value)

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun createMainComponent(project: Project): JComponent {
    val panel = JBPanelWithEmptyText(BorderLayout()).withEmptyText(VcsLogBundle.message("vcs.log.is.loading"))
    VcsLogUtil.runWhenVcsAndLogIsReady(project) { logManager ->
      val projectLog = VcsProjectLog.getInstance(project)
      val tabsManager = projectLog.tabsManager

      try {
        val factory = tabsManager.getPersistentVcsLogUiFactory(logManager, tabId, VcsLogTabLocation.EDITOR, filters)
        val ui = logManager.createLogUi(factory, VcsLogTabLocation.EDITOR)
        tabName = VcsLogTabsManager.generateDisplayName(ui)
        ui.filterUi.addFilterListener {
          tabName = VcsLogTabsManager.generateDisplayName(ui)
          VcsLogEditorUtil.updateTabName(project, ui)
        }
        if (filters != null) filters = null
        panel.add(VcsLogPanel(logManager, ui), BorderLayout.CENTER)
      }
      catch (e: CannotAddVcsLogWindowException) {
        LOG.error(e)
        panel.emptyText.text = VcsLogBundle.message("vcs.log.duplicated.tab.id.error")
      }
    }
    return panel
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystemInstance
  override fun getPath(): String {
    return fileSystemInstance.getPath(pathId)
  }

  override fun enforcePresentableName(): Boolean = true
  override fun getPresentableName(): String = tabName
  override fun getPresentablePath(): String = tabName

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultVcsLogFile

    if (tabId != other.tabId) return false

    return true
  }

  override fun hashCode(): Int {
    return tabId.hashCode()
  }

  companion object {
    private val LOG = logger<DefaultVcsLogFile>()
  }
}

class DefaultVcsLogFileTabTitleProvider : EditorTabTitleProvider, DumbAware {

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return file.tabName
  }
}

@Service(Service.Level.APP)
@State(name = "Vcs.Log.Editor.Tab.Names", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class VcsLogEditorTabNameCache : SimplePersistentStateComponent<VcsLogEditorTabNameCache.MyState>(MyState()) {

  fun getTabName(path: String) = state.pathToTabName[path]

  fun putTabName(path: String, tabName: String) {
    state.pathToTabName.remove(path)
    state.pathToTabName[path] = tabName // to put recently changed paths at the end of the linked map

    val limit = UISettings.instance.recentFilesLimit
    while (state.pathToTabName.size > limit) {
      val (firstPath, _) = state.pathToTabName.asIterable().first()
      state.pathToTabName.remove(firstPath)
    }

    state.intIncrementModificationCount()
  }

  class MyState : BaseState() {
    @get:Tag("path-to-tab-name")
    val pathToTabName by linkedMap<String, String>()
  }
}