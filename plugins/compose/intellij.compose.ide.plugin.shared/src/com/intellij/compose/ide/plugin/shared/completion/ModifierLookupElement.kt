// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.jetbrains.annotations.ApiStatus

/**
 * Inserts "Modifier." before [delegate] and imports
 * [com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN] if it's not imported.
 */
@ApiStatus.Internal
abstract class ModifierLookupElement(
  delegate: LookupElement,
  val insertModifier: Boolean,
) : LookupElementDecorator<LookupElement>(delegate) {
  companion object {
    const val CALL_ON_MODIFIER_OBJECT: String = "Modifier."
  }

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.itemText = lookupString
  }

  override fun getAllLookupStrings(): MutableSet<String> {
    if (insertModifier) {
      val lookupStrings = super.getAllLookupStrings().toMutableSet()
      lookupStrings.add(CALL_ON_MODIFIER_OBJECT + super.getLookupString())
      return lookupStrings
    }
    return super.getAllLookupStrings()
  }

  override fun getLookupString(): String {
    if (insertModifier) {
      return CALL_ON_MODIFIER_OBJECT + super.getLookupString()
    }
    return super.getLookupString()
  }
}
