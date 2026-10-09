// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.patterns.RepeatingGroupPatternBuilder

internal class RepeatingGroupPatternBuilderImpl(required: Boolean) : GroupPatternBuilderImpl(required), RepeatingGroupPatternBuilder {

  private var uniqueValue: Boolean = false

  override fun unique(value: Boolean) {
    uniqueValue = value
  }

  override val unique: Boolean get() = uniqueValue
  override val repeats: Boolean get() = true
}
