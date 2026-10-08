// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeComposedWeightItem
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * A raw search result, with the matchers that decide which parts of its presentation the UI highlights.
 */
@ApiStatus.Experimental
interface SeTargetRawItem {
  val rawItem: Any
  val weight: Int
  val matchers: ItemMatchers?
}

@ApiStatus.Internal
class SeTargetRawItemImpl(
  override val rawItem: Any,
  override val composedWeight: SeComposedWeight,
  override val matchers: ItemMatchers?,
) : SeTargetRawItem, SeComposedWeightItem {
  override val weight: Int get() = composedWeight.first
}

/**
 * A goto search result with its computed presentation.
 */
@ApiStatus.Experimental
interface SeTargetPresentableItem : SeItem {
  val extendedInfo: SeExtendedInfo
  val isMultiSelectionSupported: Boolean
  val isExactMatch: Boolean
}

@ApiStatus.Internal
class SeTargetPresentableItemImpl(rawItem: Any,
                                  private val matchers: ItemMatchers?,
                                  override val composedWeight: SeComposedWeight,
                                  private val presentation: TargetPresentation,
                                  override val extendedInfo: SeExtendedInfo,
                                  override val isMultiSelectionSupported: Boolean,
                                  override val isExactMatch: Boolean): SeTargetPresentableItem, SeComposedWeightItem {

  override val rawObject: Any = PSIPresentationBgRendererWrapper.ItemWithPresentation(rawItem, presentation)
  override fun weight(): Int = composedWeight.first
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentationBuilder()
    .withTargetPresentation(presentation, matchers, extendedInfo, isMultiSelectionSupported)
    .build()
}
