// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.docGeneration

import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.actions.PrintText
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.DocumentationProperties
import com.intellij.cce.processor.GenerateActionsProcessor

class DocGenerationActionsProcessor : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    for (token in code.getChildren()) {
      processToken(token as CodeToken)
    }
  }

  private fun processToken(token: CodeToken) {
    val properties = token.properties as DocumentationProperties
    val docLen = properties.docComment.length

    // Recompute nameIdentifierOffset after the docComment deletion
    val correctNameIdentifierOffset = when {
      properties.nameIdentifierOffset > properties.docStartOffset -> properties.nameIdentifierOffset - docLen
      else -> properties.nameIdentifierOffset
    }

    addAction(DeleteRange(properties.docStartOffset, properties.docEndOffset))
    addAction(MoveCaret(correctNameIdentifierOffset))
    addAction(CallFeature(token.text, correctNameIdentifierOffset, properties))
    addAction(MoveCaret(properties.docStartOffset))
    addAction(PrintText(properties.docComment))
  }
}
