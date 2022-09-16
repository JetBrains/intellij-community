package com.intellij.javascript.web.symbols

import com.intellij.codeInspection.ProblemDescriptor

data class WebSymbolReferenceProblem(
  val symbolTypes: Set<WebSymbol.SymbolType>,
  val kind: ProblemKind,
  val descriptor: ProblemDescriptor,
) {

  enum class ProblemKind {
    DeprecatedSymbol,
    UnknownSymbol,
    MissingRequiredPart,
    DuplicatedPart
  }
}