// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.context.WebSymbolsContextRulesProvider
import com.intellij.webSymbols.query.WebSymbolNameConversionRulesProvider

interface StaticWebSymbolsScope : WebSymbolsScope, WebSymbolsContextRulesProvider {

  override fun createPointer(): Pointer<out StaticWebSymbolsScope>

  fun getNameConversionRulesProvider(framework: FrameworkId): WebSymbolNameConversionRulesProvider
}