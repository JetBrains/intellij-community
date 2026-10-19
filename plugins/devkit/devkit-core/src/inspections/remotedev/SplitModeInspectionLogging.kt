// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal const val SPLIT_MODE_API_USAGE_SHORT_NAME: String = "SplitModeApiUsage"

/**
 * Debug log of the split-mode inspections: the PSI elements that one inspection pass visits and the problems it reports.
 *
 * It is off by default.
 * To turn it on, add `#org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionLogging` to `idea.log.debug.categories`.
 */
internal object SplitModeInspectionLogging {
  fun createRun(
    inspectionShortName: String,
    file: PsiFile,
  ): SplitModeInspectionLogRun? {
    if (!LOG.isDebugEnabled) return null
    return SplitModeInspectionLogRun(inspectionShortName, file)
  }
}

internal class SplitModeInspectionLogRun(
  private val inspectionShortName: String,
  private val file: PsiFile,
) {
  private val visitedElements = ConcurrentHashMap.newKeySet<VisitedPsiElementKey>()
  private val reportedProblems = AtomicInteger()

  fun recordVisited(element: PsiElement?) {
    val key = element?.toVisitedPsiElementKey() ?: return
    visitedElements.add(key)
  }

  fun recordProblem(element: PsiElement?) {
    recordVisited(element)
    reportedProblems.incrementAndGet()
  }

  fun logSummary() {
    LOG.debug {
      "Split mode inspection summary: inspection=$inspectionShortName, file=${file.presentablePath()}, " +
      "visitedPsiElements=${visitedElements.size}, reportedProblems=${reportedProblems.get()}"
    }
  }
}

private data class VisitedPsiElementKey(
  val elementClass: String,
  val startOffset: Int,
  val endOffset: Int,
)

private fun PsiElement.toVisitedPsiElementKey(): VisitedPsiElementKey? {
  if (!isValid) {
    return null
  }
  val range = textRange ?: return null
  return VisitedPsiElementKey(javaClass.name, range.startOffset, range.endOffset)
}

private fun PsiFile.presentablePath(): String {
  val virtualFile = originalFile.virtualFile ?: virtualFile ?: return name
  val projectRoot = project.guessProjectDir()
  if (projectRoot != null) {
    return VfsUtilCore.getRelativePath(virtualFile, projectRoot, '/') ?: virtualFile.path
  }
  return virtualFile.path
}

private val LOG = logger<SplitModeInspectionLogging>()
