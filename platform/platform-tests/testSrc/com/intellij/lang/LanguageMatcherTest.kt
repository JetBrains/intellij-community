// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.testFramework.LightPlatformTestCase

class LanguageMatcherTest : LightPlatformTestCase() {

  fun `test base matcher without dialects`() {
    val matcher = LanguageMatcher.match(MyBaseLanguage.INSTANCE)
    assertTrue(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  fun `test base matcher with dialects`() {
    val matcher = LanguageMatcher.matchWithDialects(MyBaseLanguage.INSTANCE)
    assertTrue(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  fun `test middle matcher without dialects`() {
    val matcher = LanguageMatcher.match(MyTestLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  fun `test middle matcher with dialects`() {
    val matcher = LanguageMatcher.matchWithDialects(MyTestLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  fun `test meta language without dialects`() {
    val matcher = LanguageMatcher.match(MyMetaLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  fun `test meta language with dialects`() {
    val matcher = LanguageMatcher.matchWithDialects(MyMetaLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }
}