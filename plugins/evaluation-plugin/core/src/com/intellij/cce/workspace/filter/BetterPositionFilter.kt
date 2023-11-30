// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup

class BetterPositionFilter(name: String, evaluationType: String) : ComparePositionFilter(name, evaluationType) {
  override val filterType = CompareSessionsFilter.CompareFilterType.POSITION_BETTER

  override fun check(base: Lookup, forComparing: Lookup, text: String): Boolean {
    val basePosition = base.suggestions.indexOfFirst { text == it.text }
    val forComparingPosition = forComparing.suggestions.indexOfFirst { text == it.text }
    return upper(basePosition, forComparingPosition) || exist(basePosition, forComparingPosition)
  }
}