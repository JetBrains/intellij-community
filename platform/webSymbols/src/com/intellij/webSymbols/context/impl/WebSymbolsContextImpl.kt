// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.WebSymbolsContext

internal class WebSymbolsContextImpl(private val map: Map<ContextKind, ContextName>) : WebSymbolsContext {
  override fun get(kind: ContextKind): ContextName? =
    map[kind]

  override fun toString(): String =
    map.toString()

  companion object {
    val empty = object: WebSymbolsContext {
      override fun get(kind: ContextKind): ContextName? = null
    }
  }

}
