// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.psi.PsiElement

private val LOG = logger<PolySymbolReference>()

/**
 * In dev/test builds, verify that [expectedName] - the symbol name (own references) or matched name
 * (EP references) a reference is supposed to spell out - matches (case-insensitively - e.g. HTML
 * tag/attribute names are case-insensitive by spec, so `<DIV>`/`<div>` must not trip this) the literal
 * text at [rangeInElement] within [psiElement]'s own text. [originDescription] identifies the
 * offending provider/resolver, e.g. `provider.javaClass.name` for EP-based references, or `"own
 * reference resolver for ${psiElement.javaClass.name}"` for own references (a per-PSI-class lambda has
 * no useful class name).
 *
 * An empty [rangeInElement] is a deliberate, established convention for "this reference has no
 * textual anchor" - by definition it can't spell out any non-empty name, so it's exempt from the name
 * check (bounds are still validated). Mirrors the equivalent exemption in
 * `com.intellij.polySymbols.impl.checkDeclarationSymbolNameMatchesText` for declarations.
 */
internal fun checkReferenceSymbolNameMatchesText(
  originDescription: String,
  psiElement: PsiElement,
  rangeInElement: TextRange,
  expectedName: String,
) {
  val app = ApplicationManager.getApplication() ?: return
  if (!app.isUnitTestMode && !app.isInternal && !app.isEAP) return
  val elementText = psiElement.text
  if (!TextRange(0, elementText.length).containsRange(rangeInElement.startOffset, rangeInElement.endOffset)) {
    LOG.error(
      "$originDescription produced a PolySymbol reference with range=$rangeInElement, out of bounds of " +
        "the referencing element's own text (a ${psiElement.javaClass.name}, text length ${elementText.length}). " +
        "Symbol name: '$expectedName'."
    )
    return
  }
  if (rangeInElement.isEmpty) return
  val actualText = rangeInElement.substring(elementText)
  if (!actualText.equals(expectedName, ignoreCase = true)) {
    LOG.error(
      "$originDescription produced a PolySymbol reference whose symbol name does not match (even " +
        "case-insensitively) the text at its range. PSI element: ${psiElement.javaClass.name}, " +
        "range: $rangeInElement, text at range: '$actualText', symbol name: '$expectedName'."
    )
  }
}
