// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.inspections.impl

import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.xmlb.annotations.Attribute

internal class WebSymbolsHighlightInLanguageEP {

  @Attribute("language")
  @RequiredElement
  @JvmField
  var language: String? = null

  companion object {

    private val EP_NAME = ExtensionPointName<WebSymbolsHighlightInLanguageEP>("com.intellij.webSymbols.highlightInLanguage")

    fun shouldHighlight(fileLanguage: Language): Boolean =
      supportedLanguages.get().any { supportedLang ->
        if (supportedLang is MetaLanguage) {
          supportedLang.matchesLanguage(fileLanguage)
        }
        else {
          fileLanguage.isKindOf(supportedLang)
        }
      }

    private val supportedLanguages: ClearableLazyValue<Set<Language>> = ExtensionPointUtil.dropLazyValueOnChange(
      ClearableLazyValue.create {
        EP_NAME.extensionList.mapNotNull { Language.findLanguageByID(it.language) }.toSet()
      }, EP_NAME, null
    )
  }

}