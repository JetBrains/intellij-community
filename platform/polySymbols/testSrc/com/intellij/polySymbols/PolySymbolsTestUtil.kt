// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.testFramework.query.PolySymbolsDebugOutputPrinter
import com.intellij.testFramework.PlatformTestUtil

internal val polySymbolsTestsDataPath: String
  get() = "${PlatformTestUtil.getCommunityPath()}/platform/polySymbols/testData/"

object PolySymbolsTestsDebugOutputPrinter : PolySymbolsDebugOutputPrinter() {
  override val propertiesToPrint: List<PolySymbolProperty<*>>
    get() = super.propertiesToPrint + listOf<PolySymbolProperty<Any>>(
      PolySymbolProperty["ng-binding-pattern"], PolySymbolProperty["source-file"],
      PolySymbolProperty["custom-prop"], PolySymbolProperty["custom-prop-2"]
    )
}