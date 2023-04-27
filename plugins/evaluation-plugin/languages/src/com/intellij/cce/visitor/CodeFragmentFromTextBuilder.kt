package com.intellij.cce.visitor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.cce.processor.EvaluationRootProcessor
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.util.text

class CodeFragmentFromTextBuilder : CodeFragmentBuilder() {
  override fun build(file: VirtualFile, rootProcessor: EvaluationRootProcessor): CodeFragment {
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
        } else {
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
