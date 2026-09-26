// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import icons.EditorconfigIcons
import org.editorconfig.language.util.headers.EditorConfigHeaderOverrideSearcherBase.OverrideSearchResult
import org.editorconfig.language.util.headers.EditorConfigOverridingHeaderSearcher
import javax.swing.Icon

class EditorConfigOverridingHeaderLineMarkerProvider : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher: EditorConfigOverridingHeaderSearcher = EditorConfigOverridingHeaderSearcher()

  override fun createTooltipProvider(searchResults: List<OverrideSearchResult>): (PsiElement) -> String {
    val isPartial = searchResults.any { it.isPartial }
    val isSingle = searchResults.size == 1

    val tooltip = if (isSingle) {
      val key = if (isPartial) "message.header.partially-overriding.element" else "message.header.overriding.element"
      EditorConfigBundle.get(key, searchResults.first().header.text)
    }
    else {
      val key = if (isPartial) "message.header.partially-overriding.multiple" else "message.header.overriding.multiple"
      EditorConfigBundle[key]
    }

    return { tooltip }
  }

  override fun getIcon(isPartial: Boolean, element: PsiElement): Icon {
    return if (isPartial) EditorconfigIcons.PartiallyOverriding else AllIcons.Gutter.OverridingMethod
  }

  override fun getFindUsagesTitle(isPartial: Boolean, element: PsiElement): String {
    val key = if (isPartial) "message.header.partially-overriding.find-usages-title" else "message.header.overriding.find-usages-title"
    return EditorConfigBundle.get(key, element.text)
  }
}