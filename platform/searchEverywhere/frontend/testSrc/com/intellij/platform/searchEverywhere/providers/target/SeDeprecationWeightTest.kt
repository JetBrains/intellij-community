// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeWeightComponent
import com.intellij.platform.searchEverywhere.SeWeightKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon

/**
 * A deprecated item goes below an item that is not deprecated and has the same match weight (IJPL-133314).
 */
@TestApplication
class SeDeprecationWeightTest {

  @Test
  fun deprecatedItemGoesBelowNotDeprecatedItemWithSameMatch() {
    val deprecated = weightOf(100, item(CodeInsightColors.DEPRECATED_ATTRIBUTES))
    val markedForRemoval = weightOf(100, item(CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES))
    val notDeprecated = weightOf(100, item(null))

    assertTrue(notDeprecated > deprecated, "$notDeprecated vs $deprecated")
    assertTrue(notDeprecated > markedForRemoval, "$notDeprecated vs $markedForRemoval")
  }

  @Test
  fun matchWeightDecidesBeforeDeprecation() {
    val deprecated = weightOf(100, item(CodeInsightColors.DEPRECATED_ATTRIBUTES))
    val notDeprecated = weightOf(50, item(null))

    assertTrue(deprecated > notDeprecated, "$deprecated vs $notDeprecated")
  }

  @Test
  fun itemWithSimilarityIsChecked() {
    val wrapped = PsiItemWithSimilarity(item(CodeInsightColors.DEPRECATED_ATTRIBUTES))

    assertEquals(0, notDeprecatedComponent(weightOf(100, wrapped)).weight)
  }

  @Suppress("DEPRECATION")
  @Test
  fun itemWithPresentationIsChecked() {
    val wrapped = PSIPresentationBgRendererWrapper.ItemWithPresentation(
      PsiItemWithSimilarity(item(CodeInsightColors.DEPRECATED_ATTRIBUTES)),
      TargetPresentation.builder("Owner").presentation(),
    )

    assertEquals(0, notDeprecatedComponent(weightOf(100, wrapped)).weight)
  }

  @Test
  fun componentGoesInKeyOrder() {
    val weight = SeComposedWeight.of(listOf(SeWeightComponent(SeWeightKey.MATCH, 100), SeWeightComponent(SeWeightKey.RECENCY, 7)))
    val result = ReadAction.computeBlocking<SeComposedWeight, Throwable> { withDeprecationComponent(weight, item(null)) }

    assertEquals(listOf(SeWeightKey.MATCH, SeWeightKey.RECENCY, SeWeightKey.NOT_DEPRECATED), result.components.map { it.key })
    assertEquals(listOf(100, 7, 1), result.components.map { it.weight })
  }

  private fun weightOf(match: Int, rawItem: Any): SeComposedWeight =
    ReadAction.computeBlocking<SeComposedWeight, Throwable> { withDeprecationComponent(SeComposedWeight(match), rawItem) }

  private fun notDeprecatedComponent(weight: SeComposedWeight): SeWeightComponent =
    weight.components.single { it.key == SeWeightKey.NOT_DEPRECATED }

  private fun item(key: TextAttributesKey?): NavigationItem = object : NavigationItem {
    override fun getName(): String = "Owner"

    override fun getPresentation(): ColoredItemPresentation = object : ColoredItemPresentation {
      override fun getPresentableText(): String = "Owner"
      override fun getIcon(unused: Boolean): Icon? = null
      override fun getTextAttributesKey(): TextAttributesKey? = key
    }
  }
}
