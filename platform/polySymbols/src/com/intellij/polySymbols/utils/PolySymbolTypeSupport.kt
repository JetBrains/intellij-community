// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.polySymbols.impl.PolySymbolTypeSupportTypeReferenceData

interface PolySymbolTypeSupport {

  fun resolve(types: List<TypeReference>): Any?

  interface TypeReference {
    val module: String?
    val name: String

    companion object {
      fun create(
        module: String?,
        name: String,
      ): TypeReference =
        PolySymbolTypeSupportTypeReferenceData(module, name)
    }
  }
}