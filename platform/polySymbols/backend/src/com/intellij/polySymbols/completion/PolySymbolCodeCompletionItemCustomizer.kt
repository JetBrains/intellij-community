// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolCodeCompletionItemCustomizer {

  fun customize(
    item: PolySymbolCodeCompletionItem,
    framework: FrameworkId?,
    qualifiedKind: PolySymbolQualifiedKind,
    location: PsiElement,
  ): PolySymbolCodeCompletionItem?

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolCodeCompletionItemCustomizer> =
      ExtensionPointName.create<PolySymbolCodeCompletionItemCustomizer>("com.intellij.polySymbols.codeCompletionItemCustomizer")

    internal fun Sequence<PolySymbolCodeCompletionItem>.customizeItems(
      framework: FrameworkId?,
      qualifiedKind: PolySymbolQualifiedKind,
      location: PsiElement,
    ): Sequence<PolySymbolCodeCompletionItem> {
      val customizers = EP_NAME.extensionList
      return if (customizers.isNotEmpty())
        this.mapNotNull { item ->
          customizers.foldRight(item) { customizer, acc: PolySymbolCodeCompletionItem? ->
            if (acc == null)
              null
            else customizer.customize(acc, framework, qualifiedKind, location)
          }
        }
      else this
    }
  }
}