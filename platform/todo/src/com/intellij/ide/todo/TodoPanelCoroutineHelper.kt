// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.todo.nodes.TodoItemNode
import com.intellij.ide.todo.nodes.TodoRemoteItemNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

internal class TodoPanelCoroutineHelper(private val panel: TodoPanel) : Disposable {
  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(panel, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  fun schedulePreviewPanelLayoutUpdate() {
    scope.launch(Dispatchers.EDT + ClientId.current.asContextElement()) {
      if (!panel.usagePreviewPanel.isVisible) return@launch

      val lastUserObject = TreeUtil.getLastUserObject(panel.tree.selectionPath)
      if (lastUserObject == null) {
        panel.usagePreviewPanel.updateLayout(panel.myProject, null)
        return@launch
      }

      val usageInfos = if (shouldUseSplitTodo()) {
        readAction {
          val node = lastUserObject as? TodoRemoteItemNode ?: return@readAction emptyList()
          val value = node.value ?: return@readAction emptyList()
          val psiFile = PsiManager.getInstance(panel.myProject).findFile(value.file) ?: return@readAction emptyList()
          val startOffset = value.navigationOffset
          val endOffset = value.navigationOffset + value.length
          listOf(UsageInfo(psiFile, startOffset, endOffset))
        }
      } else {
        readAction {
          val leaf = panel.treeBuilder.getFirstLeafForElement(lastUserObject)

          if (leaf is TodoItemNode) {
            val value = leaf.getValue()!!
            val psiFile = PsiDocumentManager.getInstance(panel.myProject).getPsiFile(value.document)

            if (psiFile != null) {
              val rangeMarker = value.rangeMarker
              val usageInfos = mutableListOf(
                UsageInfo(psiFile, rangeMarker.startOffset, rangeMarker.endOffset),
              )

              value.additionalRangeMarkers
                .filter { it.isValid }
                .mapTo(usageInfos) {
                  UsageInfo(psiFile, it.startOffset, it.endOffset)
                }
            }
            else {
              emptyList()
            }
          }
          else {
            emptyList()
          }
        }
      }

      panel.usagePreviewPanel.updateLayout(panel.myProject, usageInfos.ifEmpty { null })
    }
  }
}