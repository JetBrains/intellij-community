// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import java.awt.event.MouseEvent
import javax.swing.Icon

abstract class EditorConfigHeaderLineMarkerProviderBase : LineMarkerProvider {
  final override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null
  final override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    val analyzedHeaders = elements.asSequence()
      .mapNotNull { it as? EditorConfigHeader }
      .filter { it.isValidGlob }

    val currentFile = getCurrentFile(analyzedHeaders) ?: return
    val validHeaders = getValidHigherHeaders(currentFile)
    val matchingHeaders = getMatchingHeaders(analyzedHeaders, validHeaders)
    validHeaders.zip(matchingHeaders).mapNotNull { (header, matchingHeaders) ->
      if (matchingHeaders.isEmpty()) return@mapNotNull null
      val identifier = PsiTreeUtil.firstChild(header)
      LineMarkerInfo(
        identifier,
        identifier.textRange,
        icon,
        Pass.LINE_MARKERS,
        createTooltipProvider(matchingHeaders),
        createNavigationHandler(matchingHeaders),
        GutterIconRenderer.Alignment.CENTER
      )
    }.forEach { result += it }
  }

  private fun getCurrentFile(headers: Sequence<EditorConfigHeader>) =
    EditorConfigPsiTreeUtil.getOriginalFile(headers.firstOrNull()?.containingFile) as? EditorConfigPsiFile

  private fun getValidHigherHeaders(file: EditorConfigPsiFile) = findRelevantPsiFiles(file)
    .asSequence()
    .flatMap { it.sections.asSequence() }
    .map(EditorConfigSection::getHeader)
    .filter(EditorConfigHeader::isValidGlob)

  private fun getMatchingHeaders(
    headers: Sequence<EditorConfigHeader>,
    validHeaders: Sequence<EditorConfigHeader>
  ) = headers.map {
    validHeaders
      .filter(createRelevantHeaderFilter(it))
      .filter(createMatchingHeaderFilter(it))
      .toList()
  }

  fun getMatchingHeaders(header: EditorConfigHeader): List<EditorConfigHeader> {
    if (!header.isValidGlob) return emptyList()
    val currentFile = getCurrentFile(sequenceOf(header)) ?: return emptyList()
    val validHeaders = getValidHigherHeaders(currentFile)
    return getMatchingHeaders(sequenceOf(header), validHeaders).single()
  }

  private fun createTooltipProvider(matchingHeaders: List<EditorConfigHeader>) = { _: PsiElement ->
    matchingHeaders.singleOrNull()?.text?.let { EditorConfigBundle.get(tooltipKeySingular, it) }
    ?: EditorConfigBundle[tooltipKeyPlural]
  }

  private fun createNavigationHandler(matchingHeaders: List<EditorConfigHeader>) = { event: MouseEvent, psiElement: PsiElement ->
    val title = EditorConfigBundle[navigationTitleKey]
    val findUsagesTitle = EditorConfigBundle.get(findUsagesTitleKey, psiElement.text)
    val renderer = DefaultPsiElementCellRenderer()
    PsiElementListNavigator.openTargets(event, matchingHeaders.toTypedArray(), title, findUsagesTitle, renderer)
  }

  protected abstract fun findRelevantPsiFiles(file: EditorConfigPsiFile): List<EditorConfigPsiFile>
  protected abstract fun createRelevantHeaderFilter(header: EditorConfigHeader): (EditorConfigHeader) -> Boolean
  protected abstract fun createMatchingHeaderFilter(header: EditorConfigHeader): (EditorConfigHeader) -> Boolean
  protected abstract val navigationTitleKey: String
  protected abstract val findUsagesTitleKey: String
  protected abstract val tooltipKeySingular: String
  protected abstract val tooltipKeyPlural: String
  protected abstract val icon: Icon
}
