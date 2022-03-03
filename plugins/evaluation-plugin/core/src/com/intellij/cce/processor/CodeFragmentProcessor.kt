package com.intellij.cce.processor

import com.intellij.cce.core.CodeFragment

interface CodeFragmentProcessor {
  fun process(code: CodeFragment) = Unit
}
