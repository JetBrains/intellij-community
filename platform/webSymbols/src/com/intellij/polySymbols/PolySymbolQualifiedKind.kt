// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe

data class PolySymbolQualifiedKind(
  val namespace: @NlsSafe SymbolNamespace,
  val kind: @NlsSafe SymbolKind,
) {
  fun withName(name: String): PolySymbolQualifiedName = PolySymbolQualifiedName(namespace, kind, name)

  override fun toString(): String = "/$namespace/$kind"
}