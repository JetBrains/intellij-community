// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor
import com.intellij.util.xml.highlighting.DomElementResolveProblemDescriptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal const val SPLIT_MODE_API_USAGE_SHORT_NAME: String = "SplitModeApiUsage"

internal object SplitModeInspectionLogging {
  fun createRun(
    inspectionShortName: String,
    file: PsiFile,
  ): SplitModeInspectionLogRun? {
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
    LOG.warn(
      "Split mode inspection summary: inspection=$inspectionShortName, file=${file.presentablePath()}, " +
      "visitedPsiElements=${visitedElements.size}, reportedProblems=${reportedProblems.get()}"
    )
  }
}

internal fun DomElementAnnotationHolder.withSplitModeInspectionLogging(
  inspectionShortName: String,
): SplitModeDomInspectionLoggingHolder {
  val run = SplitModeInspectionLogging.createRun(inspectionShortName, fileElement.file)
  return SplitModeDomInspectionLoggingHolder(this, run)
}

internal fun DomElementAnnotationHolder.recordSplitModeInspectionVisit(element: PsiElement?) {
  (this as? SplitModeDomInspectionLoggingHolder)?.recordVisited(element)
}

internal class SplitModeDomInspectionLoggingHolder(
  private val delegate: DomElementAnnotationHolder,
  private val run: SplitModeInspectionLogRun?,
) : DomElementAnnotationHolder by delegate {
  val logRun: SplitModeInspectionLogRun?
    get() = run

  fun recordVisited(element: PsiElement?) {
    run?.recordVisited(element)
  }

  override fun createProblem(
    domElement: DomElement,
    message: String?,
    vararg fixes: LocalQuickFix,
  ): DomElementProblemDescriptor {
    run?.recordProblem(domElement.xmlElement)
    return delegate.createProblem(domElement, message, *fixes)
  }

  override fun createProblem(
    domElement: DomElement,
    highlightType: HighlightSeverity,
    message: String,
  ): DomElementProblemDescriptor {
    run?.recordProblem(domElement.xmlElement)
    return delegate.createProblem(domElement, highlightType, message)
  }

  override fun createProblem(
    domElement: DomElement,
    highlightType: HighlightSeverity,
    message: String,
    vararg fixes: LocalQuickFix,
  ): DomElementProblemDescriptor {
    run?.recordProblem(domElement.xmlElement)
    return delegate.createProblem(domElement, highlightType, message, *fixes)
  }

  override fun createProblem(
    domElement: DomElement,
    highlightType: HighlightSeverity,
    message: String,
    textRange: TextRange,
    vararg fixes: LocalQuickFix,
  ): DomElementProblemDescriptor {
    run?.recordProblem(domElement.xmlElement)
    return delegate.createProblem(domElement, highlightType, message, textRange, *fixes)
  }

  override fun createProblem(
    domElement: DomElement,
    highlightType: ProblemHighlightType,
    message: String,
    textRange: TextRange?,
    vararg fixes: LocalQuickFix,
  ): DomElementProblemDescriptor {
    run?.recordProblem(domElement.xmlElement)
    return delegate.createProblem(domElement, highlightType, message, textRange, *fixes)
  }

  override fun createResolveProblem(
    element: GenericDomValue<*>,
    reference: PsiReference,
  ): DomElementResolveProblemDescriptor {
    run?.recordProblem(element.xmlElement)
    return delegate.createResolveProblem(element, reference)
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
