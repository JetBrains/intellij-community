package com.intellij.javascript.web.symbols

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WebSymbolCodeCompletionItemCustomizer {

  fun customize(item: WebSymbolCodeCompletionItem,
                framework: FrameworkId?,
                namespace: WebSymbolsContainer.Namespace,
                kind: SymbolKind): WebSymbolCodeCompletionItem?

  companion object {
    val EP_NAME = ExtensionPointName.create<WebSymbolCodeCompletionItemCustomizer>(
      "com.intellij.javascript.web.codeCompletionItemCustomizer")

    fun Sequence<WebSymbolCodeCompletionItem>.customizeItems(framework: FrameworkId?,
                                                             namespace: WebSymbolsContainer.Namespace,
                                                             kind: SymbolKind): Sequence<WebSymbolCodeCompletionItem> {
      val customizers = EP_NAME.extensionList
      return if (customizers.isNotEmpty())
        this.mapNotNull { item ->
          customizers.foldRight(item) { customizer, acc: WebSymbolCodeCompletionItem? ->
            if (acc == null)
              null
            else customizer.customize(acc, framework, namespace, kind)
          }
        }
      else this
    }
  }
}