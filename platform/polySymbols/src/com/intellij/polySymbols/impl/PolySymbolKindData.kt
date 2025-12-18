// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolKindName
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedName
import java.util.concurrent.ConcurrentHashMap

internal data class PolySymbolKindData(
  override val namespace: @NlsSafe PolySymbolNamespace,
  override val kindName: @NlsSafe PolySymbolKindName,
) : PolySymbolKind {

  override fun withName(name: String): PolySymbolQualifiedName =
    PolySymbolQualifiedNameData(this, name)

  override fun toString(): String = "$namespace/$kindName"

  companion object {

    private val registeredSymbolKinds = ConcurrentHashMap<PolySymbolKind, PolySymbolKind>()

    fun create(namespace: PolySymbolNamespace, kindName: PolySymbolKindName): PolySymbolKind =
      registeredSymbolKinds.computeIfAbsent(PolySymbolKindData(namespace, kindName)) { it }

  }

}