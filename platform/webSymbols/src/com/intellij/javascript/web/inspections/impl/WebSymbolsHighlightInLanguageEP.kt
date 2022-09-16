// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javascript.web.inspections.impl

import com.intellij.lang.Language
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class WebSymbolsHighlightInLanguageEP {

  @Attribute("language")
  @RequiredElement
  @JvmField
  var language: String? = null

  companion object {

    private val EP_NAME = ExtensionPointName<WebSymbolsHighlightInLanguageEP>("com.intellij.javascript.web.highlightInLanguage")

    fun shouldHighlight(language: Language): Boolean =
      language.isKindOf(language.id)

    private val languages: ClearableLazyValue<Set<String>> = ExtensionPointUtil.dropLazyValueOnChange(
      ClearableLazyValue.create {
        EP_NAME.extensionList.mapNotNull { it.language }.toSet()
      }, EP_NAME, null
    )
  }

}