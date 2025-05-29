// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind

sealed interface WebSymbolReferenceProblem {
  val symbolKinds: Set<PolySymbolQualifiedKind>
  val kind: ProblemKind
  val descriptor: ProblemDescriptor

  enum class ProblemKind {
    DeprecatedSymbol,
    ObsoleteSymbol,
    UnknownSymbol,
    MissingRequiredPart,
    DuplicatedPart
  }

  companion object {
    fun create(
      symbolKinds: Set<PolySymbolQualifiedKind>,
      kind: ProblemKind,
      descriptor: ProblemDescriptor,
    ): WebSymbolReferenceProblem =
      WebSymbolReferenceProblemData(symbolKinds, kind, descriptor)
  }
}

private data class WebSymbolReferenceProblemData(
  override val symbolKinds: Set<PolySymbolQualifiedKind>,
  override val kind: ProblemKind,
  override val descriptor: ProblemDescriptor,
) : WebSymbolReferenceProblem