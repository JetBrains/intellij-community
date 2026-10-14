// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeWeightComponent
import com.intellij.platform.searchEverywhere.SeWeightKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font

/**
 * A deprecated item goes below an item that is not deprecated and has the same match weight (IJPL-133314).
 */
@TestApplication
class SeDeprecationWeightTest {

  @Test
  fun strikethroughItemGoesBelowItemWithSameMatch() {
    val struckOut = weightOf(100, presentation(EffectType.STRIKEOUT))
    val plain = weightOf(100, presentation(null))
    val noAttributes = weightOf(100, TargetPresentation.builder("Owner").presentation())

    assertTrue(plain > struckOut, "$plain vs $struckOut")
    assertTrue(noAttributes > struckOut, "$noAttributes vs $struckOut")
  }

  @Test
  fun additionalStrikeoutEffectIsChecked() {
    val attributes = TextAttributes(null, null, Color.RED, EffectType.WAVE_UNDERSCORE, Font.PLAIN)
    attributes.withAdditionalEffect(EffectType.STRIKEOUT, Color.GRAY)

    assertEquals(0, notDeprecatedComponent(weightOf(100, presentation(attributes))).weight)
  }

  @Test
  fun otherEffectIsNotDeprecation() {
    assertEquals(1, notDeprecatedComponent(weightOf(100, presentation(EffectType.WAVE_UNDERSCORE))).weight)
  }

  @Test
  fun matchWeightDecidesBeforeDeprecation() {
    val struckOut = weightOf(100, presentation(EffectType.STRIKEOUT))
    val plain = weightOf(50, presentation(null))

    assertTrue(struckOut > plain, "$struckOut vs $plain")
  }

  @Test
  fun componentGoesInKeyOrder() {
    val weight = SeComposedWeight.of(listOf(SeWeightComponent(SeWeightKey.MATCH, 100), SeWeightComponent(SeWeightKey.RECENCY, 7)))
    val result = withDeprecationComponent(weight, presentation(null))

    assertEquals(listOf(SeWeightKey.MATCH, SeWeightKey.RECENCY, SeWeightKey.NOT_DEPRECATED), result.components.map { it.key })
    assertEquals(listOf(100, 7, 1), result.components.map { it.weight })
  }

  private fun weightOf(match: Int, presentation: TargetPresentation): SeComposedWeight =
    withDeprecationComponent(SeComposedWeight(match), presentation)

  private fun notDeprecatedComponent(weight: SeComposedWeight): SeWeightComponent =
    weight.components.single { it.key == SeWeightKey.NOT_DEPRECATED }

  private fun presentation(effectType: EffectType?): TargetPresentation =
    presentation(TextAttributes(null, null, effectType?.let { Color.GRAY }, effectType, Font.PLAIN))

  private fun presentation(attributes: TextAttributes): TargetPresentation =
    TargetPresentation.builder("Owner").presentableTextAttributes(attributes).presentation()
}
