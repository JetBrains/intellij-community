// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.WebSymbolsPattern

internal interface ComplexPatternConfigProvider {

  fun getPatterns(): List<WebSymbolsPattern>

  fun getOptions(params: MatchParameters, scopeStack: Stack<WebSymbolsScope>): ComplexPatternOptions

  val isStaticAndRequired: Boolean

}