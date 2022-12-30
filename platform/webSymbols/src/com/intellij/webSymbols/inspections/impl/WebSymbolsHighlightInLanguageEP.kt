// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.inspections.impl

import com.intellij.lang.Language
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

    fun shouldHighlight(language: Language): Boolean =
      language.isKindOf(language.id)

    private val languages: ClearableLazyValue<Set<String>> = ExtensionPointUtil.dropLazyValueOnChange(
      ClearableLazyValue.create {
        EP_NAME.extensionList.mapNotNull { it.language }.toSet()
      }, EP_NAME, null
    )
  }

}