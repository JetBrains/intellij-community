// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import java.util.concurrent.ConcurrentHashMap

internal data class PolySymbolQualifiedKindData(
  override val namespace: @NlsSafe PolySymbolNamespace,
  override val kind: @NlsSafe PolySymbolKind,
) : PolySymbolQualifiedKind {

  override fun withName(name: String): PolySymbolQualifiedName =
    PolySymbolQualifiedNameData(this, name)

  override fun toString(): String = "$namespace/$kind"

  companion object {

    private val registeredQualifiedKinds = ConcurrentHashMap<PolySymbolQualifiedKind, PolySymbolQualifiedKind>()

    fun create(namespace: PolySymbolNamespace, kind: PolySymbolKind): PolySymbolQualifiedKind =
      registeredQualifiedKinds.computeIfAbsent(PolySymbolQualifiedKindData(namespace, kind)) { it }

  }

}