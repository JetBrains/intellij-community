// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.utils.PolySymbolTypeSupport

object PolySymbolsMockTypeSupport : PolySymbolTypeSupport {

  override val typeProperty: PolySymbolProperty<*>? = PolySymbolProperty["type", Any::class.java]

  override fun resolve(types: List<PolySymbolTypeSupport.TypeReference>): Any? =
    types.map {
      if (it.module != null) "${it.module}:${it.name}"
      else it.name
    }.let {
      when {
        it.isEmpty() -> null
        it.size == 1 -> it[0]
        else -> it
      }
    }
}