// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.highlighting

import com.intellij.compose.ide.plugin.resources.VALUES_DIRNAME
import com.intellij.compose.ide.plugin.resources.getComposeResourcesDir
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.kotlin.idea.base.util.module

/**
 * This annotator is used to highlight special characters inside compose resources xml files, such as \n, \t, \uXXXX, and parameters inside string resources
 */
internal class ComposeResourcesXmlAnnotator : Annotator, DumbAware {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (holder.isBatchMode()) return
    if (element !is XmlText || element.textLength == 0) return
    if (!shouldHighlight(element)) return

    highlight(element, holder)
  }

  private fun highlight(element: XmlText, holder: AnnotationHolder) {
    val text = element.text
    val startOffset = element.textRange.startOffset

    for ((rangeInElement, attributes) in findHighlightedRanges(text)) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(rangeInElement.shiftRight(startOffset))
        .textAttributes(attributes)
        .create()
    }
  }

  private fun shouldHighlight(element: PsiElement): Boolean {
    val parentTag = element.parent as? XmlTag ?: return false
    if (parentTag.name != "string" && parentTag.name != "item") return false

    val parentDirectory = element.containingFile.parent ?: return false
    if (!parentDirectory.name.startsWith(VALUES_DIRNAME)) return false

    val composeResourcesDir = element.module?.getComposeResourcesDir()
    return parentDirectory.parent?.virtualFile == composeResourcesDir
  }

}
