/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
