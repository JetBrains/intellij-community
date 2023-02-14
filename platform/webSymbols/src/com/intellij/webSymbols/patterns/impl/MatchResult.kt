// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.WebSymbolNameSegment

internal class MatchResult internal constructor(
  val segments: List<WebSymbolNameSegment>
) {

  internal constructor(segment: WebSymbolNameSegment) : this(listOf(segment))

  init {
    assert(segments.isNotEmpty())
  }

  val start: Int = segments.first().start

  val end: Int = segments.last().end

  override fun toString(): String = segments.toString()

}