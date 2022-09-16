// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javascript.web.framework.impl

import com.intellij.javascript.web.framework.WebSymbolsFramework
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import org.jetbrains.annotations.NotNull

class WebSymbolsFrameworkExtension<T> : KeyedExtensionCollector<T, String> {

  val all: Map<WebSymbolsFramework, List<T>>
    get() = WebSymbolsFramework.allAsSequence
        .mapNotNull<WebSymbolsFramework, Pair<WebSymbolsFramework, @NotNull MutableList<T>>> { framework ->
          forKey(framework.id).takeIf { it.isNotEmpty() }?.let { Pair(framework, it) }
        }
        .toMap()

  fun forAny(): List<T> = forKey("any")

  fun allFor(t: WebSymbolsFramework): List<T> = forKey(t.id)
  fun allFor(framework: String): List<T> = forKey(framework)

  constructor(epName: String) : super(epName)

  constructor(epName: ExtensionPointName<KeyedLazyInstance<T>>) : super(epName)

  override fun keyToString(key: String): String = key

}