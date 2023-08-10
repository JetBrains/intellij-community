package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher

@Remote(
  serviceInterface = "com.intellij.codeInsight.daemon.DaemonCodeAnalyzer",
  value = "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl"
)
interface DaemonCodeAnalyzer {
  fun isRunningOrPending(): Boolean

  fun isRunning(): Boolean

  fun isAllAnalysisFinished(psiFile: PsiFile): Boolean
}

fun Driver.isCodeAnalysisRunning(project: Project): Boolean {
  return withContext(OnDispatcher.EDT) {
    service(DaemonCodeAnalyzer::class, project).isRunningOrPending()
  }
}

fun Driver.isCodeAnalysisFinished(project: Project, file: VirtualFile): Boolean {
  return withContext {
    val psiFile = service(PsiManager::class, project).findFile(file)

    withContext(OnDispatcher.EDT) {
      service(DaemonCodeAnalyzer::class, project).isAllAnalysisFinished(psiFile!!)
    }
  }
}