// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.testFramework.LightPlatformTestCase
import groovy.transform.CompileStatic

@CompileStatic
class LanguageMatcherTest extends LightPlatformTestCase {

  void 'test base matcher without dialects'() {
    def matcher = LanguageMatcher.match(MyBaseLanguage.INSTANCE)
    assertTrue(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  void 'test base matcher with dialects'() {
    def matcher = LanguageMatcher.matchWithDialects(MyBaseLanguage.INSTANCE)
    assertTrue(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  void 'test middle matcher without dialects'() {
    def matcher = LanguageMatcher.match(MyTestLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  void 'test middle matcher with dialects'() {
    def matcher = LanguageMatcher.matchWithDialects(MyTestLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  void 'test meta language without dialects'() {
    def matcher = LanguageMatcher.match(MyMetaLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }

  void 'test meta language with dialects'() {
    def matcher = LanguageMatcher.matchWithDialects(MyMetaLanguage.INSTANCE)
    assertFalse(matcher.matchesLanguage(MyBaseLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage.INSTANCE))
    assertTrue(matcher.matchesLanguage(MyTestLanguage2.INSTANCE))
    assertFalse(matcher.matchesLanguage(MyMetaLanguage.INSTANCE))
  }
}
