// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe

data class PolySymbolQualifiedKind(
  val namespace: @NlsSafe PolySymbolNamespace,
  val kind: @NlsSafe PolySymbolKind,
) {
  fun withName(name: String): PolySymbolQualifiedName = PolySymbolQualifiedName(this, name)

  override fun toString(): String = "/$namespace/$kind"
}