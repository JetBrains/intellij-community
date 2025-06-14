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

  fun isAllAnalysisFinished(psiFile: PsiFile): Boolean
  fun getHighlights(document: Document, severity: HighlightSeverity?, project: Project): List<HighlightInfo>
}

@Remote("com.intellij.codeInsight.daemon.impl.HighlightInfo")
interface HighlightInfo {
  fun getDescription(): String
  fun getSeverity(): HighlightSeverity
  fun getText(): String
  fun getHighlighter(): RangeHighlighterEx
}

@Remote("com.intellij.lang.annotation.HighlightSeverity")
interface HighlightSeverity {
  fun getName(): String
}

@Remote("com.intellij.openapi.editor.ex.RangeHighlighterEx")
interface RangeHighlighterEx {
  fun getTextAttributesKey(): TextAttributesKey
}

@Remote("com.intellij.codeInsight.daemon.impl.HighlightInfoType")
interface HighlightInfoType {
  fun getSeverity(psiElement: PsiElement): HighlightSeverity
  fun getAttributesKey(): TextAttributesKey
}

@Remote("com.intellij.openapi.editor.colors.TextAttributesKey")
interface TextAttributesKey {
  fun compareTo(key: TextAttributesKey): Int
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
    waitFor("No Code analysis", timeout) {
      isCodeAnalysisFinished(project, file)
    }
  }
}

fun Driver.getHighlights(document: Document): List<HighlightInfo> {
  return withReadAction {
    service<DaemonCodeAnalyzer>(singleProject()).getHighlights(document, null, singleProject())
  }
}