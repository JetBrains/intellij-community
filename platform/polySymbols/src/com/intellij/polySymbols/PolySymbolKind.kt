// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.impl.PolySymbolKindData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolKind {

  val namespace: @NlsSafe PolySymbolNamespace

  val kindName: @NlsSafe PolySymbolKindName

  fun withName(name: String): PolySymbolQualifiedName

  companion object {

    @JvmStatic
    operator fun get(namespace: PolySymbolNamespace, kindName: PolySymbolKindName): PolySymbolKind =
      PolySymbolKindData.create(namespace, kindName)

  }

}