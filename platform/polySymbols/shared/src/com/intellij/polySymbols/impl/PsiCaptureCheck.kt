// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.DependencyHandle
import com.intellij.psi.PsiElement

private val PSI_OR_POLY_SYMBOL_TYPES = arrayOf(
  PsiElement::class.java,
  PolySymbol::class.java,
)

private val LOG = logger<DependencyHandle<*>>()

/**
 * In dev/test builds, reflect on [lambda] and fail fast if any captured
 * field holds a [PsiElement] or [PolySymbol] directly — those are only
 * valid within one read action, so they must be wrapped in `dependency(…)`
 * to survive.
 */
internal fun checkNoPsiCapture(lambda: Any, context: String) {
  val app = ApplicationManager.getApplication() ?: return
  if (!app.isUnitTestMode && !app.isInternal && !app.isEAP) return
  for (field in lambda::class.java.declaredFields) {
    val fieldType = field.type
    for (forbidden in PSI_OR_POLY_SYMBOL_TYPES) {
      if (forbidden.isAssignableFrom(fieldType)) {
        LOG.error(
          "$context lambda captures a ${forbidden.simpleName} " +
          "(${fieldType.name} as field ${field.name}). Declare it with dependency(...) " +
          "so it survives read-action boundaries."
        )
      }
    }
  }
}
