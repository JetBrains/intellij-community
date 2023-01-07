// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.webSymbols.registry.WebSymbolsRegistryCompletionQueryTest
import com.intellij.webSymbols.registry.WebSymbolsRegistryNameQueryTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  WebSymbolsRegistryCompletionQueryTest::class,
  WebSymbolsRegistryNameQueryTest::class,
)
class WebSymbolsTestSuite