// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolQualifiedName {

  val namespace: @NlsSafe PolySymbolNamespace

  val kind: PolySymbolKind

  val name: @NlsSafe String

  fun withName(name: String): PolySymbolQualifiedName

  fun matches(kind: PolySymbolKind): Boolean

  fun matches(kind: PolySymbolKind, vararg kinds: PolySymbolKind): Boolean

  companion object {

    @JvmStatic
    operator fun get(namespace: PolySymbolNamespace, kindName: PolySymbolKindName, name: String): PolySymbolQualifiedName =
      PolySymbolKind[namespace, kindName].withName(name)
  }

}