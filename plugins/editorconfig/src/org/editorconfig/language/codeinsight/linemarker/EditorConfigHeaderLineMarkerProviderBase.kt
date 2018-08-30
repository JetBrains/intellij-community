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
      .toList()

    val currentFile =
      EditorConfigPsiTreeUtil
        .getOriginalFile(analyzedHeaders.firstOrNull()?.containingFile)
        as? EditorConfigPsiFile ?: return
    val psiFiles = findRelevantPsiFiles(currentFile)
    val validHeaders = psiFiles
      .asSequence()
      .flatMap { it.sections.asSequence() }
      .map(EditorConfigSection::getHeader)
      .filter(EditorConfigHeader::isValidGlob)
      .toList()

    for (analyzedHeader in analyzedHeaders) {
      val relevantSections = validHeaders.filter(createRelevantHeaderFilter(analyzedHeader))
      val matchingHeaders = relevantSections.filter(createMatchingHeaderFilter(analyzedHeader))
      if (matchingHeaders.isEmpty()) continue
      val identifier = PsiTreeUtil.firstChild(analyzedHeader)
      val marker = LineMarkerInfo(
        identifier,
        identifier.textRange,
        icon,
        Pass.LINE_MARKERS,
        createTooltipProvider(matchingHeaders),
        createNavigationHandler(matchingHeaders),
        GutterIconRenderer.Alignment.CENTER
      )

      result.add(marker)
    }
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
