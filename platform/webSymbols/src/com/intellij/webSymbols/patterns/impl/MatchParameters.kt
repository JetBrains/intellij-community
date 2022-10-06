// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.registry.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.registry.WebSymbolsRegistry

internal open class MatchParameters(val name: String,
                                    val registry: WebSymbolsRegistry) {

  constructor(name: String, params: WebSymbolsNameMatchQueryParams)
    : this(name, params.registry)

  val framework: String? get() = registry.framework

  override fun toString(): String =
    "match: $name (framework: $framework)"
}