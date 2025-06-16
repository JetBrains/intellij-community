// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.reference.findChildren
import java.awt.event.MouseEvent

class EditorConfigOverriddenKeyLineMarkerProvider : LineMarkerProvider, DumbAware {
  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    for (element in elements) {
      if (element !is EditorConfigFlatOptionKey) continue
      val identifier = element.firstChild ?: continue
      if (identifier.firstChild != null) continue
      val children = element.findChildren()
        .toTypedArray()

      if (children.isEmpty()) continue
      val marker = LineMarkerInfo(
        identifier,
        identifier.textRange,
        AllIcons.Gutter.OverridenMethod,
        createTooltipProvider(children),
        createNavigationHandler(children, element),
        GutterIconRenderer.Alignment.RIGHT
      )

      result.add(marker)
    }
  }

  private fun createNavigationHandler(children: Array<EditorConfigFlatOptionKey>, optionKey: EditorConfigFlatOptionKey) =
    { event: MouseEvent, _: PsiElement ->
      val title = EditorConfigBundle["message.overridden.title"]
      val findUsagesTitle = EditorConfigBundle.get("message.overridden.find-usages-title", optionKey.text, optionKey.declarationSite)
      val renderer = DefaultPsiElementCellRenderer()
      PsiElementListNavigator.openTargets(event, children, title, findUsagesTitle, renderer)
    }

  private fun createTooltipProvider(children: Array<EditorConfigFlatOptionKey>): (PsiElement) -> String = {
    if (children.size == 1) {
      val site = children.single().declarationSite
      EditorConfigBundle.get("message.overridden.element", site)
    }
    else {
      EditorConfigBundle["message.overridden.multiple"]
    }
  }
}
