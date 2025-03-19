// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.docGeneration

import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.DocumentationProperties
import com.intellij.cce.processor.GenerateActionsProcessor

class DocGenerationActionsProcessor : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    actions {
      for (token in code.getChildren()) {
        session {
          processToken(token as CodeToken)
        }
      }
    }
  }

  private fun ActionsBuilder.SessionBuilder.processToken(token: CodeToken) {
    val properties = token.properties as DocumentationProperties
    val docLen = properties.docComment.length

    // Recompute nameIdentifierOffset after the docComment deletion
    val correctNameIdentifierOffset = when {
      properties.nameIdentifierOffset > properties.docStartOffset -> properties.nameIdentifierOffset - docLen
      else -> properties.nameIdentifierOffset
    }

    deleteRange(properties.docStartOffset, properties.docEndOffset)
    moveCaret(correctNameIdentifierOffset)
    callFeature(token.text, correctNameIdentifierOffset, properties)
    moveCaret(properties.docStartOffset)
    printText(properties.docComment)
  }
}
