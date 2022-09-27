// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.codeInspection.ProblemDescriptor

data class WebSymbolReferenceProblem(
  val symbolKinds: Set<WebSymbolQualifiedKind>,
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