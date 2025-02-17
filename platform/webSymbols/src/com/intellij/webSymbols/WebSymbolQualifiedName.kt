// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.util.NlsSafe

data class WebSymbolQualifiedName(
  val namespace: @NlsSafe SymbolNamespace,
  val kind: @NlsSafe SymbolKind,
  val name: @NlsSafe String,
) {
  val qualifiedKind: WebSymbolQualifiedKind = WebSymbolQualifiedKind(namespace, kind)

  fun matches(qualifiedKind: WebSymbolQualifiedKind): Boolean =
    qualifiedKind.kind == kind && qualifiedKind.namespace == namespace

  fun matches(qualifiedKind: WebSymbolQualifiedKind, vararg qualifiedKinds: WebSymbolQualifiedKind): Boolean =
    sequenceOf(qualifiedKind).plus(qualifiedKinds).any(::matches)

  override fun toString(): String = "/$namespace/$kind/$name"
}