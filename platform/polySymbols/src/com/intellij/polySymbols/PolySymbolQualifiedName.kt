// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolQualifiedName {

  val qualifiedKind: PolySymbolQualifiedKind

  val name: @NlsSafe String

  val kind: @NlsSafe PolySymbolKind

  val namespace: @NlsSafe PolySymbolNamespace

  fun withName(name: String): PolySymbolQualifiedName

  fun matches(qualifiedKind: PolySymbolQualifiedKind): Boolean

  fun matches(qualifiedKind: PolySymbolQualifiedKind, vararg qualifiedKinds: PolySymbolQualifiedKind): Boolean

  companion object {

    @JvmStatic
    operator fun get(namespace: PolySymbolNamespace, kind: PolySymbolKind, name: String): PolySymbolQualifiedName =
      PolySymbolQualifiedKind[namespace, kind].withName(name)
  }

}