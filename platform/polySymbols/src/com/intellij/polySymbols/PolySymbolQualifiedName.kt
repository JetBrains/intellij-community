// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe

data class PolySymbolQualifiedName(
  val qualifiedKind: PolySymbolQualifiedKind,
  val name: @NlsSafe String,
) {

  val kind: @NlsSafe PolySymbolKind get() = qualifiedKind.kind

  val namespace: @NlsSafe PolySymbolNamespace get() = qualifiedKind.namespace

  fun matches(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    this.qualifiedKind == qualifiedKind

  fun matches(qualifiedKind: PolySymbolQualifiedKind, vararg qualifiedKinds: PolySymbolQualifiedKind): Boolean =
    sequenceOf(qualifiedKind).plus(qualifiedKinds).any(::matches)

  override fun toString(): String = "${qualifiedKind.namespace}/${qualifiedKind.kind}/$name"
}