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
import org.editorconfig.language.util.headers.EditorConfigHeaderSearcher
import java.awt.event.MouseEvent
import javax.swing.Icon

abstract class EditorConfigHeaderLineMarkerProviderBase : LineMarkerProvider {
  final override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null
  final override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    val headers = elements.mapNotNull { it as? EditorConfigHeader }
    if (headers.isEmpty()) return

    val relevantHeaders = searcher.getRelevantHeaders(headers.first())
    for (header in headers) {
      if (!header.isValidGlob) continue
      val matchingHeaders = searcher.getMatchingHeaders(header, relevantHeaders)
      val info = createLineMarkerInfo(PsiTreeUtil.firstChild(header), matchingHeaders) ?: continue
      result.add(info)
    }
  }

  private fun createLineMarkerInfo(identifier: PsiElement,
                                   matchingHeaders: List<EditorConfigHeader>): LineMarkerInfo<PsiElement>? {
    if (matchingHeaders.isEmpty()) return null
    val tooltip =
      if (matchingHeaders.size == 1) EditorConfigBundle.get(tooltipKeySingular, matchingHeaders.first().text)
      else EditorConfigBundle[tooltipKeyPlural]

    return LineMarkerInfo(
      identifier,
      identifier.textRange,
      icon,
      Pass.LINE_MARKERS,
      { tooltip },
      createNavigationHandler(matchingHeaders),
      GutterIconRenderer.Alignment.RIGHT
    )
  }

  private fun createNavigationHandler(matchingHeaders: List<EditorConfigHeader>) = { event: MouseEvent, psiElement: PsiElement ->
    val title = EditorConfigBundle[navigationTitleKey]
    val findUsagesTitle = EditorConfigBundle.get(findUsagesTitleKey, psiElement.text)
    val renderer = DefaultPsiElementCellRenderer()
    PsiElementListNavigator.openTargets(event, matchingHeaders.toTypedArray(), title, findUsagesTitle, renderer)
  }

  protected abstract val searcher: EditorConfigHeaderSearcher
  protected abstract val navigationTitleKey: String
  protected abstract val findUsagesTitleKey: String
  protected abstract val tooltipKeySingular: String
  protected abstract val tooltipKeyPlural: String
  protected abstract val icon: Icon
}
