// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.testGeneration

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor

class TestGenerationActionsProcessor : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    actions {
      for (token in code.getChildren()) {
        check(token is CodeToken)
        session {
          moveCaret(token.offset)
          callFeature(token.text, token.offset, token.properties)
        }
      }
    }
  }
}
