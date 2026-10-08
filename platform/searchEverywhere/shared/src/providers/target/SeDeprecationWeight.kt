// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeWeightComponent
import com.intellij.platform.searchEverywhere.SeWeightKey
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Puts the [SeWeightKey.NOT_DEPRECATED] component of [rawItem] into [weight].
 */
@RequiresReadLock
fun withDeprecationComponent(weight: SeComposedWeight, rawItem: Any): SeComposedWeight {
  val component = SeWeightComponent(SeWeightKey.NOT_DEPRECATED, if (isDeprecated(rawItem)) 0 else 1)
  return weight.with(component)
}

/**
 * Reads the text attributes key of the item presentation. The PSI presentation shows the same key as a strikethrough.
 */
@Suppress("DEPRECATION")
private fun isDeprecated(rawItem: Any): Boolean {
  val navigationItem = (PSIPresentationBgRendererWrapper.toPsi(rawItem)
                        ?: PSIPresentationBgRendererWrapper.getItem(rawItem)) as? NavigationItem ?: return false
  if (navigationItem is PsiElement && !navigationItem.isValid) return false

  val key = (navigationItem.presentation as? ColoredItemPresentation)?.textAttributesKey
  return key == CodeInsightColors.DEPRECATED_ATTRIBUTES || key == CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES
}
