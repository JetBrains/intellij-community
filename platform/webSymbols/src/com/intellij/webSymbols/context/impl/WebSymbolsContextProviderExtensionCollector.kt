// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import com.intellij.webSymbols.context.WebSymbolsContextProvider

class WebSymbolsContextProviderExtensionCollector(private val epName: ExtensionPointName<WebSymbolsContextProviderExtensionPoint>)
  : KeyedExtensionCollector<WebSymbolsContextProvider, String>(epName) {

  fun allKinds(): Set<String> =
    epName.extensions.asSequence()
      .mapNotNull { it.kind }
      .toSet()

  fun allOf(kind: String): Map<String /* name */, List<WebSymbolsContextProvider>> =
    epName.extensions.asSequence()
      .filter { it.kind == kind && it.name != null }
      .groupBy { it.name!! }
      .mapValues { (_, list) -> list.map { it.instance } }

  fun forAny(kind: String): List<WebSymbolsContextProvider> = forKey("$kind:any")

  fun allFor(kind: String, name: String): List<WebSymbolsContextProvider> = forKey("$kind:$name")

  override fun keyToString(key: String): String = key

}