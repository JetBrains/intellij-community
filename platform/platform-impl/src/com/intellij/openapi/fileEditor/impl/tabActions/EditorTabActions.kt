// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.TabInfo

class EditorTabActions(val tab: TabInfo,
                       val file: VirtualFile,
                       val project: Project,
                       val editorWindow: EditorWindow,
                       parentDisposable: Disposable) {
  val dataContext = DataManager.getInstance().getDataContext(tab.component)
  /*SimpleDataContext.getSimpleContext(EditorWindow.DATA_KEY.name, tab.component,
                                                       DataManager.getInstance().getDataContext(tab.component))*/
  val closeTab = CloseTab(tab.component, file, project, editorWindow, parentDisposable)

  init {
      val editorActionGroup = ActionManager.getInstance().getAction(
        "EditorTabActionGroup") as DefaultActionGroup
      val group = DefaultActionGroup()

      val event = AnActionEvent.createFromDataContext("EditorTabActionGroup", null, dataContext)

      for (action in editorActionGroup.getChildren(event)) {
        if (action is ActionGroup) {
          group.addAll(action.getChildren(event).toList())
        }
        else {
          group.addAction(action)
        }
      }
      group.addAction(closeTab, Constraints.LAST)

      tab.setTabLabelActions(group, ActionPlaces.EDITOR_TAB)
  }
}