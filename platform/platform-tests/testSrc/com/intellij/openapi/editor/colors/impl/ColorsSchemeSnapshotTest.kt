// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import kotlinx.collections.immutable.ImmutableMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font

internal class ColorsSchemeSnapshotTest {
  @Test
  fun `a with function returns the same snapshot for the same value object`() {
    val current = newSnapshot()
      .withColor(FIRST, Color.RED)
      .withAttributes(FIRST_NAME, BOLD)
    assertThat(current.withColor(FIRST, Color.RED)).isSameAs(current)
    assertThat(current.withAttributes(FIRST_NAME, BOLD)).isSameAs(current)
    assertThat(current.withAllColors(mapOf(FIRST to Color.RED))).isSameAs(current)
    assertThat(current.withAllAttributes(emptyMap())).isSameAs(current)
  }

  @Test
  fun `a change makes a new snapshot and keeps the old one`() {
    val before = newSnapshot().withColor(FIRST, Color.RED)
    val after = before.withColor(FIRST, Color.BLUE)
    assertThat(before.getColor(FIRST)).isEqualTo(Color.RED)
    assertThat(after.getColor(FIRST)).isEqualTo(Color.BLUE)
  }

  @Test
  fun `withAllAttributes replaces the text attributes of the same key name`() {
    val before = newSnapshot().withAttributes(FIRST_NAME, BOLD)
    val after = before.withAllAttributes(mapOf(FIRST_NAME to ITALIC, SECOND_NAME to BOLD))
    assertThat(after.attributes()).containsExactlyInAnyOrderEntriesOf(mapOf(FIRST_NAME to ITALIC, SECOND_NAME to BOLD))
  }

  @Test
  fun `withMissing keeps the own values and adds the missing ones`() {
    val own = newSnapshot().withColor(FIRST, Color.RED)
    val other = newSnapshot()
      .withColor(FIRST, Color.BLUE)
      .withColor(SECOND, Color.GREEN)
      .withAttributes(FIRST_NAME, BOLD)
    val result = own.withMissing(other)
    assertThat(result.colors()).containsExactlyInAnyOrderEntriesOf(mapOf(FIRST to Color.RED, SECOND to Color.GREEN))
    assertThat(result.getAttributes(FIRST_NAME)).isSameAs(BOLD)
    assertThat(result.withMissing(other)).isSameAs(result)
  }

  @Test
  fun `a without function removes only the matching values`() {
    val before = newSnapshot()
      .withColor(FIRST, Color.RED)
      .withColor(SECOND, Color.BLUE)
      .withAttributes(FIRST_NAME, BOLD)
    val after = before
      .withoutColors { _, color -> color == Color.BLUE }
      .withoutAttributes { keyName, _ -> keyName == FIRST_NAME }
    assertThat(after.colors()).containsExactlyEntriesOf(mapOf(FIRST to Color.RED))
    assertThat(after.attributes()).isEmpty()
    assertThat(after.withoutColors { _, _ -> false }).isSameAs(after)
  }

  @Test
  fun `the maps of a snapshot are immutable`() {
    val current = newSnapshot()
      .withColor(FIRST, Color.RED)
      .withAttributes(FIRST_NAME, BOLD)
    assertThat(current.colors()).isInstanceOf(ImmutableMap::class.java)
    assertThat(current.attributes()).isInstanceOf(ImmutableMap::class.java)
  }

  @Test
  fun `a state with function returns the same snapshot for the same value`() {
    val current = newSnapshot()
      .withSchemeName("Scheme")
      .withCanBeDeleted(false)
    assertThat(current.withSchemeName("Scheme")).isSameAs(current)
    assertThat(current.withCanBeDeleted(false)).isSameAs(current)
    assertThat(current.withParentScheme(null)).isSameAs(current)
  }

  @Test
  fun `withColorsAndAttributesOf takes only the colors and the text attributes`() {
    val source = newSnapshot()
      .withSchemeName("Source")
      .withCanBeDeleted(false)
      .withColor(FIRST, Color.RED)
      .withAttributes(FIRST_NAME, BOLD)
    val target = newSnapshot().withSchemeName("Target")
    val result = target.withColorsAndAttributesOf(source)
    assertThat(result.schemeName()).isEqualTo("Target")
    assertThat(result.canBeDeleted()).isTrue()
    assertThat(result.fontPreferences()).isSameAs(target.fontPreferences())
    assertThat(result.colors()).isSameAs(source.colors())
    assertThat(result.attributes()).isSameAs(source.attributes())
  }

  @Test
  fun `a font with function returns the same snapshot for the same value`() {
    val current = newSnapshot()
    assertThat(current.withFontPreferences(current.fontPreferences())).isSameAs(current)
    assertThat(current.withConsoleFontPreferences(current.consoleFontPreferences())).isSameAs(current)
    assertThat(current.withUseAppFontPreferencesInEditor(true)).isSameAs(current)
    assertThat(current.withUseEditorFontPreferencesInConsole(true)).isSameAs(current)
    assertThat(current.withFontPreferences(FontPreferencesSnapshot.EMPTY.withLineSpacing(2f))).isNotSameAs(current)
    assertThat(current.withUseAppFontPreferencesInEditor(false)).isNotSameAs(current)
  }

  @Test
  fun `a new scheme uses the application font in the editor and the editor font in the console`() {
    val current = newSnapshot()
    assertThat(current.useAppFontPreferencesInEditor()).isTrue()
    assertThat(current.useEditorFontPreferencesInConsole()).isTrue()
    assertThat(current.fontPreferences()).isSameAs(FontPreferencesSnapshot.EMPTY)
    assertThat(current.consoleFontPreferences()).isSameAs(FontPreferencesSnapshot.EMPTY)
  }

  @Test
  fun `withFontsOf takes only the fonts and their flags`() {
    val fonts = FontPreferencesSnapshot.EMPTY.withLineSpacing(2f)
    val consoleFonts = FontPreferencesSnapshot.EMPTY.withLineSpacing(1.5f)
    val source = newSnapshot()
      .withSchemeName("Source")
      .withColor(FIRST, Color.RED)
      .withFontPreferences(fonts)
      .withUseAppFontPreferencesInEditor(false)
      .withConsoleFontPreferences(consoleFonts)
      .withUseEditorFontPreferencesInConsole(false)
    val target = newSnapshot().withSchemeName("Target")
    val result = target.withFontsOf(source)
    assertThat(result.schemeName()).isEqualTo("Target")
    assertThat(result.colors()).isEmpty()
    assertThat(result.fontPreferences()).isSameAs(fonts)
    assertThat(result.useAppFontPreferencesInEditor()).isFalse()
    assertThat(result.consoleFontPreferences()).isSameAs(consoleFonts)
    assertThat(result.useEditorFontPreferencesInConsole()).isFalse()
    assertThat(target.withFontsOf(target)).isSameAs(target)
  }

  @Test
  fun `a change of the text attributes removes the temporary attributes`() {
    val current = newSnapshot().withTempAttributes(TEMP_NAME, BOLD)
    assertThat(current.getTempAttributes(TEMP_NAME)).isSameAs(BOLD)
    assertThat(current.withAttributes(FIRST_NAME, ITALIC).getTempAttributes(TEMP_NAME)).isNull()
    val source = newSnapshot().withAttributes(FIRST_NAME, ITALIC)
    assertThat(current.withColorsAndAttributesOf(source).getTempAttributes(TEMP_NAME)).isNull()
  }

  @Test
  fun `a change of other values keeps the temporary attributes`() {
    val current = newSnapshot()
      .withAttributes(FIRST_NAME, ITALIC)
      .withTempAttributes(TEMP_NAME, BOLD)
    assertThat(current.withColor(FIRST, Color.RED).getTempAttributes(TEMP_NAME)).isSameAs(BOLD)
    assertThat(current.withSchemeName("Renamed").getTempAttributes(TEMP_NAME)).isSameAs(BOLD)
    val fonts = FontPreferencesSnapshot.EMPTY.withLineSpacing(2f)
    assertThat(current.withFontPreferences(fonts).getTempAttributes(TEMP_NAME)).isSameAs(BOLD)
    assertThat(current.withAttributes(FIRST_NAME, ITALIC)).isSameAs(current)
    assertThat(current.withTempAttributes(TEMP_NAME, BOLD)).isSameAs(current)
  }

  @Test
  fun `the font families of a snapshot come from its own and its temporary attributes`() {
    val own = attributesWithFontFamilies("Noto Serif")
    val temp = attributesWithFontFamilies("Noto Sans")
    val current = newSnapshot()
      .withAttributes(FIRST_NAME, own)
      .withAttributes(SECOND_NAME, BOLD)
      .withTempAttributes(TEMP_NAME, temp)
    assertThat(current.textAttributesFontFamilies()).containsExactlyInAnyOrder(own.fontFamilies, temp.fontFamilies)
    assertThat(newSnapshot().textAttributesFontFamilies()).isEmpty()
  }

  @Test
  fun `a new snapshot has the font families of its new attributes and the old one keeps its own`() {
    val before = newSnapshot().withAttributes(FIRST_NAME, attributesWithFontFamilies("Noto Serif"))
    assertThat(before.textAttributesFontFamilies()).containsExactly(FontFamilies.of("Noto Serif"))
    val after = before.withAttributes(FIRST_NAME, BOLD)
    assertThat(after.textAttributesFontFamilies()).isEmpty()
    assertThat(before.textAttributesFontFamilies()).containsExactly(FontFamilies.of("Noto Serif"))
  }

  private fun attributesWithFontFamilies(name: String): TextAttributes {
    val attributes = TextAttributes()
    attributes.fontFamilies = FontFamilies.of(name)
    return attributes
  }

  private fun newSnapshot(): ColorsSchemeSnapshot {
    return ColorsSchemeSnapshot.initial()
  }

  private companion object {
    val FIRST: ColorKey = ColorKey.createColorKey("ColorsSchemeSnapshotTest.first")
    val SECOND: ColorKey = ColorKey.createColorKey("ColorsSchemeSnapshotTest.second")
    const val FIRST_NAME = "ColorsSchemeSnapshotTest.firstAttributes"
    const val SECOND_NAME = "ColorsSchemeSnapshotTest.secondAttributes"
    const val TEMP_NAME = "ColorsSchemeSnapshotTest.tempAttributes"
    val BOLD = TextAttributes(null, null, null, null, Font.BOLD)
    val ITALIC = TextAttributes(null, null, null, null, Font.ITALIC)
  }
}
