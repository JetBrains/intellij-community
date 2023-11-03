// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.webSymbols.context.WebSymbolsContextProvider
import java.util.concurrent.ConcurrentHashMap

class WebSymbolsContextProviderExtensionCollector(private val epName: ExtensionPointName<WebSymbolsContextProviderExtensionPoint>)
  : KeyedExtensionCollector<WebSymbolsContextProvider, String>(epName) {

  private val allKinds = ClearableLazyValue.create {
    epName.extensions.asSequence()
      .mapNotNull { it.kind }
      .toSet()
  }

  private val allOfCache = ConcurrentHashMap<String, Map<String, List<WebSymbolsContextProvider>>>()

  init {
    epName.addChangeListener(Runnable {
      allKinds.drop()
      allOfCache.clear()
    }, null)
  }

  fun allKinds(): Set<String> =
    allKinds.value

  fun allOf(kind: String): Map<String /* name */, List<WebSymbolsContextProvider>> =
    allOfCache.computeIfAbsent(kind) {
      epName.extensionList.asSequence()
        .filter { it.kind == kind && it.name != null }
        .groupBy { it.name!! }
        .mapValues { (_, list) -> list.map { it.instance } }
    }

  fun forAny(kind: String): List<WebSymbolsContextProvider> = forKey("$kind:any")

  fun allFor(kind: String, name: String): List<WebSymbolsContextProvider> = forKey("$kind:$name")

  override fun keyToString(key: String): String = key

}