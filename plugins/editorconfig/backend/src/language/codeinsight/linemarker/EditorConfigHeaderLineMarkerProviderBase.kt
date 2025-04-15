// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.util.headers.EditorConfigHeaderOverrideSearcherBase
import org.editorconfig.language.util.headers.EditorConfigHeaderOverrideSearcherBase.OverrideSearchResult
import org.editorconfig.language.util.isValidGlob
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.Icon

abstract class EditorConfigHeaderLineMarkerProviderBase : LineMarkerProvider, DumbAware {
  final override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null
  final override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    val headers = elements.mapNotNull { it as? EditorConfigHeader }
    if (headers.isEmpty()) return

    val relevantHeaders = searcher.getRelevantHeaders(headers.first())
    for (header in headers) {
      if (!header.isValidGlob) continue
      val matchingHeaders = searcher.findMatchingHeaders(header, relevantHeaders)
      val info = createLineMarkerInfo(PsiTreeUtil.firstChild(header), matchingHeaders) ?: continue
      result.add(info)
    }
  }

  private fun createNavigationHandler(searchResults: List<OverrideSearchResult>) = { event: MouseEvent, psiElement: PsiElement ->
    val isPartial = searchResults.any { it.isPartial }
    val title = EditorConfigBundle["message.header.override.title"]
    val findUsagesTitle = getFindUsagesTitle(isPartial, psiElement)
    val renderer = DefaultPsiElementCellRenderer()
    // todo icons
    PsiElementListNavigator.openTargets(event, searchResults.map { it.header }.toTypedArray(), title, findUsagesTitle, renderer)
  }

  private fun createLineMarkerInfo(identifier: PsiElement,
                                   searchResults: List<OverrideSearchResult>): LineMarkerInfo<PsiElement>? {
    if (searchResults.isEmpty()) return null

    val searchResult = searchResults.find { it.isPartial } ?: searchResults.first()
    val icon = getIcon(searchResult.isPartial, searchResult.header)

    return LineMarkerInfo(
      identifier,
      identifier.textRange,
      icon,
      createTooltipProvider(searchResults),
      createNavigationHandler(searchResults),
      GutterIconRenderer.Alignment.RIGHT
    )
  }

  abstract val searcher: EditorConfigHeaderOverrideSearcherBase
  abstract fun createTooltipProvider(searchResults: List<OverrideSearchResult>): (PsiElement) -> String
  abstract fun getIcon(isPartial: Boolean, element: PsiElement): Icon
  @Nls
  abstract fun getFindUsagesTitle(isPartial: Boolean, element: PsiElement): String
}
