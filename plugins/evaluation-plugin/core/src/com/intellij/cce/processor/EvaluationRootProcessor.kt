// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.core.CodeFragment

abstract class EvaluationRootProcessor : CodeFragmentProcessor {
  abstract fun getRoot(): CodeFragment?
}

class DefaultEvaluationRootProcessor : EvaluationRootProcessor() {
  private var evaluationRoot: CodeFragment? = null

  override fun process(code: CodeFragment) {
    evaluationRoot = code
  }

  override fun getRoot() = evaluationRoot
}

class EvaluationRootByRangeProcessor(private val startOffset: Int, private val endOffset: Int) : EvaluationRootProcessor() {
  private var evaluationRoot: CodeFragment? = null

  override fun process(code: CodeFragment) {
    evaluationRoot = CodeFragment(startOffset, endOffset - startOffset)
    evaluationRoot?.path = code.path
    evaluationRoot?.text = code.text
    for (token in code.getChildren()) {
      if (token.offset in startOffset..endOffset) evaluationRoot?.addChild(token)
    }
  }

  override fun getRoot() = evaluationRoot
}