// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockReqAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val project = e.project ?: return@launch
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return@launch
      val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return@launch

      val smartPointerManager = SmartPointerManager.getInstance(project)

      val target = readAction {
        val element = psiFile.findElementAt(editor.caretModel.offset) ?: return@readAction null
        val psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

        when {
          psiMethod != null -> {
            val methodPtr = smartPointerManager.createSmartPsiElementPointer<PsiMethod>(psiMethod)
            AnalysisTarget.Method(methodPtr)
          }
          psiClass != null -> {
            val classPtr = smartPointerManager.createSmartPsiElementPointer<PsiClass>(psiClass)
            AnalysisTarget.Class(classPtr)
          }
          else -> {
            val filePtr = smartPointerManager.createSmartPsiElementPointer<PsiJavaFile>(psiFile as PsiJavaFile)
            AnalysisTarget.File(filePtr)
          }
        }
      } ?: return@launch

      val service = project.service<LockReqsService>()
      when (target) {
        is AnalysisTarget.Method -> service.analyzeMethod(target.methodPtr)
        is AnalysisTarget.Class -> service.analyzeClass(target.classPtr)
        is AnalysisTarget.File -> service.analyzeFile(target.filePtr)
      }
    }
    // project.service<LockReqsService>().updateResults(method)
  }

  override fun update(e: AnActionEvent) {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)
    e.presentation.isEnabledAndVisible = psiFile is PsiJavaFile
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private sealed class AnalysisTarget {
    data class Method(val methodPtr: SmartPsiElementPointer<PsiMethod>) : AnalysisTarget()
    data class Class(val classPtr: SmartPsiElementPointer<PsiClass>) : AnalysisTarget()
    data class File(val filePtr: SmartPsiElementPointer<PsiJavaFile>) : AnalysisTarget()
  }
}