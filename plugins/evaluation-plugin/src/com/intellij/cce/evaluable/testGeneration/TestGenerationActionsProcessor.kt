// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.testGeneration

import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor

class TestGenerationActionsProcessor : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    for (token in code.getChildren()) {
      processToken(token as CodeToken)
    }
  }

  private fun processToken(token: CodeToken) {
    addAction(MoveCaret(token.offset))
    addAction(CallFeature(token.text, token.offset, token.properties))
  }
}
