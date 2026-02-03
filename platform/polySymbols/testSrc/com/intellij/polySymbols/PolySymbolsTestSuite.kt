// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.query.PolySymbolsCompletionQueryTest
import com.intellij.polySymbols.query.PolySymbolsListQueryTest
import com.intellij.polySymbols.query.PolySymbolsNameQueryTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  PolySymbolsCompletionQueryTest::class,
  PolySymbolsNameQueryTest::class,
  PolySymbolsListQueryTest::class,
)
class PolySymbolsTestSuite