// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsCollector.Companion.getTextRangeWithoutLeadingCommentsAndWhitespaces
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

internal class VcsCodeAuthorPlaceholdersCollector(
  editor: Editor,
  private val filter: (PsiElement) -> Boolean,
) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!filter.invoke(element)) return true

    val range = getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
    val placeholder = factory.smallTextWithoutBackground("")

    sink.addBlockElement(range.startOffset, false, true, BlockInlayPriority.CODE_AUTHOR, placeholder)
    return true
  }
}