// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.util.NlsSafe

data class WebSymbolQualifiedKind(
  val namespace: @NlsSafe SymbolNamespace,
  val kind: @NlsSafe SymbolKind,
) {
  fun toQualifiedName(name: String) = WebSymbolQualifiedName(namespace, kind, name)

  fun matches(expectedNamespace: SymbolNamespace, expectedKind: SymbolKind): Boolean {
    return namespace == expectedNamespace && kind == expectedKind
  }

  fun matches(expectedNamespace: SymbolNamespace, expectedKinds: List<SymbolKind>): Boolean {
    return namespace == expectedNamespace && expectedKinds.any { kind == it }
  }
}