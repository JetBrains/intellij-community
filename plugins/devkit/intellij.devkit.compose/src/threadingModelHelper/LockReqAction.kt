// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.threadingModelHelper

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMethod
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps

class LockReqAction : AnAction() {

  companion object {
    private const val TOOLWINDOW_ID: String = "LockReqs"
  }

  override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val project = e.project ?: return@launch
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return@launch

      withContext(Dispatchers.EDT) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID)

        if (toolWindow == null) {
          registerToolWindow(toolWindowManager).activate { }
        }
        else {
          toolWindow.activate { }
        }
      }
      val target = readAction {

        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return@readAction null

        val smartPointerManager = SmartPointerManager.getInstance(project)

        val element = LockReqPsiOps.forLanguage(psiFile.language).extractTargetElement(psiFile, editor.caretModel.offset) ?: return@readAction null

        smartPointerManager.createSmartPsiElementPointer<PsiMethod>(element)
      } ?: return@launch

      val service = project.service<LockReqsService>()
      service.analyzeMethod(target)
    }
  }

  fun registerToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(
      RegisterToolWindowTask(
        id = TOOLWINDOW_ID,
        anchor = ToolWindowAnchor.BOTTOM,
        component = null,
        icon = LockReqIcons.LockReqIcon,
        contentFactory = LockReqsToolWindowFactory(),
        stripeTitle = DevkitComposeBundle.messagePointer("tab.title.locking.requirements"),
      )
    )
    return toolWindow
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = shouldBeEnabled(e)
    e.presentation.icon = LockReqIcons.LockReqIcon
  }

  private fun shouldBeEnabled(e: AnActionEvent): Boolean {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return false
    val caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: return false
    return LockReqPsiOps.forLanguage(psiFile.language).extractTargetElement(psiFile, caretOffset) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}