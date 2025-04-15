// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.reference.findParents
import java.awt.event.MouseEvent

class EditorConfigOverridingKeyLineMarkerProvider : LineMarkerProvider, DumbAware {
  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    for (element in elements) {
      if (element !is EditorConfigFlatOptionKey) continue
      val parents = element
        .findParents()
        .toTypedArray()

      if (parents.isEmpty()) continue
      val identifier = element.firstChild ?: continue
      if (identifier.firstChild != null) continue

      val marker = LineMarkerInfo(
        identifier,
        identifier.textRange,
        AllIcons.Gutter.OverridingMethod,
        createTooltipProvider(parents),
        createNavigationHandler(parents),
        GutterIconRenderer.Alignment.RIGHT
      )

      result.add(marker)
    }
  }

  private fun createNavigationHandler(parents: Array<EditorConfigFlatOptionKey>) = { event: MouseEvent, psiElement: PsiElement ->
    val title = EditorConfigBundle["message.overriding.title"]
    val findUsagesTitle = EditorConfigBundle.get("message.overriding.find-usages-title", psiElement.text)
    val renderer = DefaultPsiElementCellRenderer()
    PsiElementListNavigator.openTargets(event, parents, title, findUsagesTitle, renderer)
  }

  private fun createTooltipProvider(parents: Array<EditorConfigFlatOptionKey>): (PsiElement) -> String = {
    if (parents.size == 1) {
      val parent = parents.single()
      EditorConfigBundle.get("message.overriding.element", parent.declarationSite)
    }
    else EditorConfigBundle["message.overriding.multiple"]
  }
}
