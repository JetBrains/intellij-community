// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.impl.PolySymbolQualifiedKindData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolQualifiedKind {

  val namespace: @NlsSafe PolySymbolNamespace

  val kind: @NlsSafe PolySymbolKind

  fun withName(name: String): PolySymbolQualifiedName

  companion object {

    @JvmStatic
    operator fun get(namespace: PolySymbolNamespace, kind: PolySymbolKind): PolySymbolQualifiedKind =
      PolySymbolQualifiedKindData.create(namespace, kind)

  }

}