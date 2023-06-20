// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf

import com.intellij.cce.actions.*
import com.intellij.cce.core.*
import com.intellij.cce.processor.GenerateActionsProcessor

class LineCompletionProcessor : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    code.getChildren().forEach {
      addActions(it as CodeLine)
    }
  }

  private fun addActions(line: CodeLine) {
    if (line.text.isNotEmpty()) {
      addAction(MoveCaret(line.offset))
      addAction(CallFeature(line.text, line.offset,
                            LineProperties(line.getChildren().map { TextRange(it.offset, it.offset + it.text.length) }))
      )
    }
  }
}
