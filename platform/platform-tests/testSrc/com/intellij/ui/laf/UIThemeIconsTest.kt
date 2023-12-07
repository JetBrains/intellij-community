// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * @author Konstantin Bulenkov
 */
class UIThemeIconsTest {
  private val basePath = "/com/intellij/ide/ui/laf/icons"
  private val intelliJIcons = "$basePath/intellij/"
  private val darculaIcons = "$basePath/darcula/"

  @Test
  fun componentIconsLocation() {
    arrayOf("checkBox.svg", "radio.svg")
      .forEach {
        assertThat(loadIconText(false, it)).describedAs(iconsAreMovedMsg(false)).isNotNull
        assertThat(loadIconText(true, it)).describedAs(iconsAreMovedMsg(true)).isNotNull
      }
  }

  private fun iconsAreMovedMsg(isDark: Boolean) = "Laf icons are moved from '" + toPath(isDark) + "'. Please fix PaletteScopeManager.getScopeByURL and the test"
  private fun toPath(isDark: Boolean) = if (isDark) darculaIcons else intelliJIcons

  @Test
  fun checkboxColors() {
    doCheckColorInFile("Checkbox.Background.Default", "checkBox.svg")
    doCheckColorInFile("Checkbox.Background.Disabled", "checkBoxDisabled.svg")
    doCheckColorInFile("Checkbox.Border.Default", "checkBox.svg")
    doCheckColorInFile("Checkbox.Border.Disabled", "checkBoxDisabled.svg")
    doCheckColorInFile("Checkbox.Focus.Thin.Default", "checkBoxFocused.svg")
    doCheckColorInFile("Checkbox.Focus.Wide", "checkBoxSelectedFocused.svg")
    doCheckColorInFile("Checkbox.Foreground.Disabled", "checkBoxSelectedDisabled.svg")
    doCheckColorInFile("Checkbox.Background.Selected", "checkBoxSelected.svg")
    doCheckColorInFile("Checkbox.Border.Selected", "checkBoxSelected.svg")
    doCheckColorInFile("Checkbox.Foreground.Selected", "checkBoxSelected.svg")
    doCheckColorInFile("Checkbox.Focus.Thin.Selected", "checkBoxSelectedFocused.svg")
  }

  private fun doCheckColorInFile(colorName: String, filename: String) {
    val lightColor = UITheme.getColorPalette()[colorName]!!.toLowerCase()
    val darkColor = UITheme.getColorPalette()["$colorName.Dark"]!!.toLowerCase()
    val lightSvg = loadIconText(false, filename)!!.toLowerCase()
    val darkSvg = loadIconText(true, filename)!!.toLowerCase()
    assertThat(lightSvg.contains(lightColor)).describedAs(msgColorsDontMatch(lightColor, filename, colorName, false)).isTrue()
    assertThat(darkSvg.contains(darkColor)).describedAs(msgColorsDontMatch(darkColor, filename, colorName, true)).isTrue()
  }

  private fun msgColorsDontMatch(lightColor: String, filename: String, colorName: String, isDark: Boolean): String {
    val path = toPath(isDark) + filename
    val key = if (isDark) "$colorName.Dark" else colorName
    return "$lightColor is not presented in $path. Please fix the icon or update color key $key in UITheme class"
  }

  private fun loadIconText(isDark: Boolean, filename: String): String? {
    return this.javaClass.getResourceAsStream(toPath(isDark) + filename)?.bufferedReader()?.readText()
  }
}