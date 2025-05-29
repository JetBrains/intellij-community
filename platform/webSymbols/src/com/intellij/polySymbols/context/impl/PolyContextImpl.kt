// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context.impl

import com.intellij.polySymbols.ContextKind
import com.intellij.polySymbols.ContextName
import com.intellij.polySymbols.context.PolyContext

internal class PolyContextImpl(private val map: Map<ContextKind, ContextName>) : PolyContext {
  override fun get(kind: ContextKind): ContextName? =
    map[kind]

  override fun toString(): String =
    map.toString()

  companion object {
    val empty = object : PolyContext {
      override fun get(kind: ContextKind): ContextName? = null
    }
  }

}
