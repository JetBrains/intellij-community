// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder


/**
 * Interface describes a probe that can be set to the {@link com.intellij.lang.impl.PsiBuilderImpl.DIAGNOSTICS} for processing different
 * building events, e.g. parser rollbacks.
 */
interface SyntaxBuildingDiagnostics {
  /**
   * Invoked on builder creation
   * @param charLength length of the text to parse in characters
   * @param tokensLength length of the text to parse in tokens
   */
  fun registerPass(charLength: Int, tokensLength: Int)

  /**
   * Invoked on marker rollback
   * @param tokens number of tokens rolled back with this marker
   */
  fun registerRollback(tokens: Int)
}

interface DiagnosticAwareBuilder {
  val lexingTimeNs: Long
}

internal var DIAGNOSTICS: SyntaxBuildingDiagnostics? = null

fun <T> computeWithDiagnostics(diagnostics: SyntaxBuildingDiagnostics?, block: () -> T): T {
  val oldDiagnostics = DIAGNOSTICS
  try {
    DIAGNOSTICS = diagnostics
    return block()
  }
  finally {
    DIAGNOSTICS = oldDiagnostics
  }
}