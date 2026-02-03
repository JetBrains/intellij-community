// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolKindName
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedName

internal data class PolySymbolQualifiedNameData(
  override val kind: PolySymbolKind,
  override val name: @NlsSafe String,
) : PolySymbolQualifiedName {

  override val namespace: @NlsSafe PolySymbolNamespace get() = kind.namespace

  override fun withName(name: String): PolySymbolQualifiedName =
    PolySymbolQualifiedNameData(kind, name)

  override fun matches(kind: PolySymbolKind): Boolean =
    this.kind == kind

  override fun matches(kind: PolySymbolKind, vararg kinds: PolySymbolKind): Boolean =
    sequenceOf(kind).plus(kinds).any(::matches)

  override fun toString(): String = "${kind.namespace}/${kind.kindName}/$name"
}