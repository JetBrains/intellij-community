// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.AppUIUtil
import com.intellij.ui.content.Content
import com.intellij.util.Alarm
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JTree

@ApiStatus.Internal
open class FrontendChangeListTodosPanel(
  todoView: TodoView,
  settings: TodoPanelSettings,
  content: Content,
) : TodoPanel(todoView, settings, false, content) {

  private val myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  private lateinit var myBuilder: FrontendChangeListTodosTreeBuilder
  private var myFileIds: List<VirtualFileId> = emptyList()
  private var myTabName: @NlsContexts.TabTitle String = VcsBundle.message("todo.tab.title.all.changes")

  override fun createTreeBuilder(tree: JTree, project: Project): TodoTreeBuilder {
    myBuilder = FrontendChangeListTodosTreeBuilder(tree, project)
    myBuilder.init()
    return myBuilder
  }

  private fun rebuildWithAlarm() {
    rebuildWithAlarm(myAlarm)
  }

  private fun updateTabName() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject) {
      setDisplayName(myTabName)
    }
  }

  fun applyState(tabName: @NlsContexts.TabTitle String, fileIds: List<VirtualFileId>) {
    val filesChanged = myFileIds != fileIds
    val tabNameChanged = myTabName != tabName

    myFileIds = fileIds
    myTabName = tabName

    if (filesChanged) {
      if (::myBuilder.isInitialized) {
        myBuilder.setFiles(fileIds)
      }
      rebuildWithAlarm()
    }
    if (tabNameChanged) {
      updateTabName()
    }
  }

  companion object {
    fun getTabName(areChangeListsEnabled: Boolean, defaultChangeListName: @Nls String?): @NlsContexts.TabTitle String {
      if (areChangeListsEnabled && defaultChangeListName != null) {
        @NlsSafe val changelistName = defaultChangeListName.trim()
        val suffix = VcsBundle.message("todo.tab.title.changelist.suffix")
        return if (StringUtil.endsWithIgnoreCase(changelistName, suffix)) changelistName
        else "$changelistName $suffix"
      }
      else {
        return VcsBundle.message("todo.tab.title.all.changes")
      }
    }
  }
}