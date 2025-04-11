// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import icons.EditorconfigIcons
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.util.headers.EditorConfigHeaderOverrideSearcherBase.OverrideSearchResult
import org.editorconfig.language.util.headers.EditorConfigOverriddenHeaderSearcher
import javax.swing.Icon

class EditorConfigOverriddenHeaderLineMarkerProvider : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher: EditorConfigOverriddenHeaderSearcher = EditorConfigOverriddenHeaderSearcher()

  override fun createTooltipProvider(searchResults: List<OverrideSearchResult>): (PsiElement) -> String {
    val isPartial = searchResults.any { it.isPartial }
    val isSingle = searchResults.size == 1

    val tooltip = if (isSingle) {
      val key = if (isPartial) "message.header.partially-overridden.element" else "message.header.overridden.element"
      EditorConfigBundle.get(key, searchResults.first().header.text)
    }
    else {
      val key = if (isPartial) "message.header.partially-overridden.multiple" else "message.header.overridden.multiple"
      EditorConfigBundle[key]
    }

    return { tooltip }
  }

  override fun getIcon(isPartial: Boolean, element: PsiElement): Icon {
    return if (isPartial) EditorconfigIcons.PartiallyOverridden else AllIcons.Gutter.OverridenMethod
  }

  override fun getFindUsagesTitle(isPartial: Boolean, element: PsiElement): String {
    val key = if (isPartial) "message.header.partially-overridden.find-usages-title" else "message.header.overridden.find-usages-title"
    return EditorConfigBundle.get(key, element.text)
  }
}