// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.declarations.PolySymbolDeclaration
import com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider

private val LOG = logger<PolySymbolDeclaration>()

/**
 * In dev/test builds, verify [declaration]'s symbol name matches (case-insensitively - e.g. HTML
 * tag/attribute names are case-insensitive by spec, so `<DIV>`/`<div>` must not trip this) the
 * literal text at its [PolySymbolDeclaration.getRangeInDeclaringElement] within its
 * [PolySymbolDeclaration.getDeclaringElement]'s own text. A provider that violates even that loose a
 * check silently corrupts downstream offset math (e.g. PolySymbolHighlightingAnnotator's
 * per-declaration highlight range), which then crashes far away from the actual bug with no clue
 * which provider/element/symbol is at fault. Catch it here instead.
 *
 * An empty [PolySymbolDeclaration.getRangeInDeclaringElement] is a deliberate, established convention
 * for "this symbol has no textual anchor in the declaring element" (e.g. a whole-file resource symbol
 * registered purely for find-usages/rename/navigation-as-a-target, with no `class_name`-like token to
 * point at) - by definition it can't spell out any non-empty name, so it's exempt from the name check
 * (bounds are still validated).
 */
internal fun checkDeclarationSymbolNameMatchesText(provider: PolySymbolDeclarationProvider, declaration: PolySymbolDeclaration) {
  val app = ApplicationManager.getApplication() ?: return
  if (!app.isUnitTestMode && !app.isInternal && !app.isEAP) return
  val declaringElement = declaration.declaringElement
  val range = declaration.rangeInDeclaringElement
  val elementText = declaringElement.text
  if (!TextRange(0, elementText.length).containsRange(range.startOffset, range.endOffset)) {
    LOG.error(
      "PolySymbolDeclarationProvider ${provider.javaClass.name} returned a declaration for symbol " +
        "'${declaration.symbol.name}' with rangeInDeclaringElement=$range, out of bounds of the " +
        "declaring element's own text (a ${declaringElement.javaClass.name}, text length ${elementText.length})."
    )
    return
  }
  if (range.isEmpty) return
  val actualText = range.substring(elementText)
  val symbolName = declaration.symbol.name
  if (!actualText.equals(symbolName, ignoreCase = true)) {
    LOG.error(
      "PolySymbolDeclarationProvider ${provider.javaClass.name} returned a declaration whose symbol name " +
        "does not match (even case-insensitively) the text at its range. PSI element: " +
        "${declaringElement.javaClass.name}, range: $range, text at range: '$actualText', symbol name: '$symbolName'."
    )
  }
}
