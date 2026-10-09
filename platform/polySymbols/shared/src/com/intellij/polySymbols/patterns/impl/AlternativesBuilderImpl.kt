// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.patterns.AlternativesBuilder
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternBuilder

internal class AlternativesBuilderImpl : AlternativesBuilder {

  private val branches: MutableList<PolySymbolPattern> = mutableListOf()

  override fun branch(body: PolySymbolPatternBuilder.() -> Unit) {
    branches += PolySymbolPatternBuilderImpl().apply(body).buildSingle()
  }

  internal fun buildBranches(): List<PolySymbolPattern> {
    check(branches.isNotEmpty()) { "oneOf must contain at least one branch" }
    return branches.toList()
  }
}
