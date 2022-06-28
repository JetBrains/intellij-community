package com.intellij.cce.processor

import com.intellij.cce.actions.CodeGolfSession
import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty

class CodeGolfProcessor : GenerateActionsProcessor() {
  private val regexLines = Regex("[^\r\n]+")
  private val leadingTabsRegex = Regex("^\\s+")

  override fun process(code: CodeFragment) {
    regexLines.findAll(code.text).forEach {
      addActions(it.range, it.value)
    }
  }

  private fun addActions(range: IntRange, value: String) {
    val leadingTabs = leadingTabsRegex.find(value)?.range?.length ?: 0
    var withoutTabs = value.drop(leadingTabs)
    val commentId = withoutTabs.indexOf("#")
    withoutTabs = withoutTabs.substring(0, if (commentId < 0) withoutTabs.length else commentId).trim()

    if (withoutTabs.isNotEmpty()) {
      val nodeProperties = SimpleTokenProperties.create(TypeProperty.LINE, SymbolLocation.UNKNOWN) {}
      addAction(DeleteRange(leadingTabs + range.first, leadingTabs + range.first + withoutTabs.length))
      addAction(MoveCaret(leadingTabs + range.first))
      addAction(CodeGolfSession(withoutTabs, nodeProperties))
    }
  }
}

private val IntRange.length: Int
  get() {
    return last - first + 1
  }
