package org.jetbrains.completion.full.line.platform.filter

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import org.jetbrains.completion.full.line.platform.FullLineLookupElement

class FullLineCharFilter : CharFilter() {
  override fun acceptChar(char: Char, prefixLength: Int, lookup: Lookup?): Result? {
    val current = lookup?.currentItem ?: return null
    val isSelectedFullLine = current is FullLineLookupElement
    if (!isSelectedFullLine) return null

    return when (char) {
      '(', '[', '<', '>', ']', ')', ':', ';', '{', '}' -> Result.HIDE_LOOKUP
      ' ' -> if (current.lookupString.contains(" ")) {
        Result.ADD_TO_PREFIX
      }
      else {
        Result.SELECT_ITEM_AND_FINISH_LOOKUP
      }
      // '\t', '\n'
      else -> Result.ADD_TO_PREFIX
    }
  }
}
