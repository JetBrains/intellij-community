// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.devkit.threading.icons.DevkitThreadingIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps
import java.util.EnumSet

private const val TOOLWINDOW_ID: String = "LockReqs"

internal abstract class BaseReqSearchAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  abstract val requirements: EnumSet<ConstraintType>

  override fun actionPerformed(e: AnActionEvent) {
    e.coroutineScope.launch(Dispatchers.Default) {
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
        val element = LockReqPsiOps.forLanguage(psiFile.language).extractTargetElement(psiFile, editor.caretModel.offset)
                      ?: return@readAction null

        SmartPointerManager.getInstance(project).createSmartPsiElementPointer<PsiMethod>(element)
      } ?: return@launch

      project.service<LockReqsService>().analyzeMethod(target, requirements)
    }
  }

  fun registerToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(
      RegisterToolWindowTask(
        id = TOOLWINDOW_ID,
        anchor = ToolWindowAnchor.BOTTOM,
        component = null,
        icon = DevkitThreadingIcons.LockRequirements,
        contentFactory = LockReqsToolWindowFactory(),
        stripeTitle = DevkitThreadingBundle.messagePointer("tab.title.locking.requirements"),
      )
    )
    return toolWindow
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = shouldBeEnabled(e)
  }

  private fun shouldBeEnabled(e: AnActionEvent): Boolean {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return false
    val caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: return false
    return LockReqPsiOps.forLanguageOrNull(psiFile.language)?.extractTargetElement(psiFile, caretOffset) != null
  }
}