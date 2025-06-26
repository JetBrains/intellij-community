// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName

internal data class PolySymbolQualifiedNameData(
  override val qualifiedKind: PolySymbolQualifiedKind,
  override val name: @NlsSafe String,
) : PolySymbolQualifiedName {

  override val kind: @NlsSafe PolySymbolKind get() = qualifiedKind.kind

  override val namespace: @NlsSafe PolySymbolNamespace get() = qualifiedKind.namespace

  override fun withName(name: String): PolySymbolQualifiedName =
    PolySymbolQualifiedNameData(qualifiedKind, name)

  override fun matches(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    this.qualifiedKind == qualifiedKind

  override fun matches(qualifiedKind: PolySymbolQualifiedKind, vararg qualifiedKinds: PolySymbolQualifiedKind): Boolean =
    sequenceOf(qualifiedKind).plus(qualifiedKinds).any(::matches)

  override fun toString(): String = "${qualifiedKind.namespace}/${qualifiedKind.kind}/$name"
}