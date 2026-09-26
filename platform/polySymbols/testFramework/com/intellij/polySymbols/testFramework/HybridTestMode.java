// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework;

public enum HybridTestMode {
  /**
   * Like {@link com.intellij.testFramework.fixtures.BasePlatformTestCase}, i.e. lightweight, with in-memory files
   */
  BasePlatform,

  /**
   * Like {@link com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase}, i.e. heavyweight, with real files
   */
  CodeInsightFixture
}
