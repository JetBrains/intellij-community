// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LanguageTestUtil {
  public static void assertAllLanguagesHaveDifferentDisplayNames() {
    Collection<Language> languages = Language.getRegisteredLanguages();
    Map<String, Language> displayNames = new HashMap<>();
    for (Language language : languages) {
      Language prev = displayNames.put(language.getDisplayName(), language);
      if (prev != null) {
        TestCase.fail("The languages '%s' (%s) and '%s' (%s) have the same display name '%s'"
               .formatted(prev, prev.getClass().getName(), language, language.getClass().getName(), language.getDisplayName()));
      }
    }
  }
}
