package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Remote(
  serviceInterface = "com.intellij.codeInsight.daemon.DaemonCodeAnalyzer",
  value = "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl"
)
interface DaemonCodeAnalyzer {
  fun isRunningOrPending(): Boolean

  fun isRunning(): Boolean

  fun isAllAnalysisFinished(psiFile: PsiFile): Boolean
}

fun Driver.isCodeAnalysisRunning(project: Project? = null): Boolean {
  return withContext(OnDispatcher.EDT) {
    service<DaemonCodeAnalyzer>(project ?: singleProject()).isRunningOrPending()
  }
}

fun Driver.isCodeAnalysisFinished(project: Project? = null, file: VirtualFile): Boolean {
  return withReadAction {
    val forProject = project ?: singleProject()
    val psiFile = service<PsiManager>(forProject).findFile(file)
    service<DaemonCodeAnalyzer>(forProject).isAllAnalysisFinished(psiFile!!)
  }
}

fun Driver.waitForCodeAnalysis(project: Project? = null, file: VirtualFile, timeout: Duration = 1.minutes) {
  withContext {
    waitFor(timeout) {
      isCodeAnalysisFinished(project, file)
    }
  }
}