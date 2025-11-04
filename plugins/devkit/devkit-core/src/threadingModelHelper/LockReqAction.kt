// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockReqAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val project = e.project ?: return@launch
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return@launch

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

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = shouldBeEnabled(e)
  }

  private fun shouldBeEnabled(e: AnActionEvent): Boolean {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return false
    val caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: return false
    return LockReqPsiOps.forLanguage(psiFile.language).extractTargetElement(psiFile, caretOffset) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}