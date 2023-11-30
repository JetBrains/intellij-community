// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.EvaluationRootProcessor
import com.intellij.cce.util.text
import com.intellij.openapi.vfs.VirtualFile

class CodeFragmentFromTextBuilder : CodeFragmentBuilder() {
  override fun build(file: VirtualFile, rootProcessor: EvaluationRootProcessor, featureName: String): CodeFragment {
    val text = file.text()
    val codeFragment = CodeFragment(0, text.length)
    codeFragment.text = text
    codeFragment.path = file.path
    var offset = 0
    for (line in text.lines()) {
      var curToken = ""
      var tokenOffset = 0
      for (ch in line) {
        if (ch == '_' || ch.isLetter() || (ch.isDigit() && curToken.isNotEmpty())) {
          if (curToken.isEmpty()) tokenOffset = offset
          curToken += ch
        }
        else {
          codeFragment.addChild(CodeToken(curToken, tokenOffset))
          curToken = ""
        }
        offset++
      }
      if (curToken.isNotEmpty()) codeFragment.addChild(CodeToken(curToken, tokenOffset))
      offset++
    }
    return findRoot(codeFragment, rootProcessor)
  }
}
