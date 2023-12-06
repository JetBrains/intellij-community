// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolQualifiedKind

interface WebSymbolCodeCompletionItemCustomizer {

  fun customize(item: WebSymbolCodeCompletionItem,
                framework: FrameworkId?,
                qualifiedKind: WebSymbolQualifiedKind,
                location: PsiElement): WebSymbolCodeCompletionItem?

  companion object {
    private val EP_NAME = ExtensionPointName.create<WebSymbolCodeCompletionItemCustomizer>(
      "com.intellij.webSymbols.codeCompletionItemCustomizer")

    internal fun Sequence<WebSymbolCodeCompletionItem>.customizeItems(
      framework: FrameworkId?,
      qualifiedKind: WebSymbolQualifiedKind,
      location: PsiElement
    ): Sequence<WebSymbolCodeCompletionItem> {
      val customizers = EP_NAME.extensionList
      return if (customizers.isNotEmpty())
        this.mapNotNull { item ->
          customizers.foldRight(item) { customizer, acc: WebSymbolCodeCompletionItem? ->
            if (acc == null)
              null
            else customizer.customize(acc, framework, qualifiedKind, location)
          }
        }
      else this
    }
  }
}