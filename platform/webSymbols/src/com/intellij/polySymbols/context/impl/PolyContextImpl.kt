// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context.impl

import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContext

internal class PolyContextImpl(private val map: Map<PolyContextKind, PolyContextName>) : PolyContext {
  override fun get(kind: PolyContextKind): PolyContextName? =
    map[kind]

  override fun toString(): String =
    map.toString()

  companion object {
    val empty = object : PolyContext {
      override fun get(kind: PolyContextKind): PolyContextName? = null
    }
  }

}
