// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolDelegate

/**
 * We need to render documentation for lookup elements. Regular `WebSymbol` does not implement
 * `DocumentationSymbol` to have a context aware documentation, so the symbol needs to be wrapped
 * for code completion.
 */
class CodeCompletionWebSymbolWithDocumentation(delegate: WebSymbol) : WebSymbolDelegate<WebSymbol>(delegate), DocumentationSymbol {
  override fun createPointer(): Pointer<CodeCompletionWebSymbolWithDocumentation> {
    val delegate = delegate.createPointer()
    return Pointer {
      delegate.dereference()?.let { CodeCompletionWebSymbolWithDocumentation(it) }
    }
  }

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.getDocumentationTarget(null)
}