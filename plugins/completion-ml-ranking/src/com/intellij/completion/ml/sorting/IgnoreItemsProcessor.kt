// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface IgnoreItemsProcessor {
  fun ignored(items: Iterable<LookupElement>): List<LookupElement>

  companion object {
    private val EP_NAME: LanguageExtension<IgnoreItemsProcessor> = LanguageExtension("com.intellij.completion.ml.ignoreItemsForSorting")

    fun forLanguage(language: Language): List<IgnoreItemsProcessor> {
      return EP_NAME.allForLanguageOrAny(language)
    }
  }
}
