// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions.base

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class EditorTabBaseAction<T>(val provider: EditorTabDataProvider<T>) : AnAction(), DumbAware {
  protected fun getList(e: AnActionEvent): List<T> {
    e.project?.let { project ->
      e.getData(PlatformDataKeys.VIRTUAL_FILE)?.let { file ->
        return provider.getList(project, file)
      }
    }
    return emptyList()
  }
}

interface EditorTabDataProvider<T> {
  fun getList(project: Project, file: VirtualFile): List<T>
}


interface EditorTabActionFactory<T> {
  fun createTabAction(i: Int): EditorTabBaseAction<T>
  fun createTabMoreAction(max: Int): EditorTabBaseAction<T>
}

abstract class EditorTabAction<T>(val maxCount: Int, factory: EditorTabActionFactory<T>) : ActionGroup(), DumbAware {
  private val actions = mutableListOf<EditorTabBaseAction<T>>()

  init {
    for (i in 0 until maxCount) {
      actions.add(factory.createTabAction(i))
    }
    actions.add(factory.createTabMoreAction(maxCount))
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return actions.toTypedArray()
  }
}




