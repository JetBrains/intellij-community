// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

/**
 * @author Konstantin Bulenkov
 */
class UIThemeIconsTest: LightPlatformTestCase() {
  val basePath = "/com/intellij/ide/ui/laf/icons"
  val intelliJIcons = "$basePath/intellij/"
  val darculaIcons = "$basePath/darcula/"

  fun testComponentIconsLocation() {
    arrayOf("checkBox.svg", "radio.svg")
      .forEach {
        TestCase.assertNotNull(iconsAreMovedMsg(false), loadIconText(false, it))
        TestCase.assertNotNull(iconsAreMovedMsg(true), loadIconText(true, it))
      }
  }

  private fun iconsAreMovedMsg(isDark: Boolean) = "Laf icons are moved from '" + toPath(isDark) + "'. Please fix PaletteScopeManager.getScopeByURL and the test"
  private fun toPath(isDark: Boolean) = if (isDark) darculaIcons else intelliJIcons

  fun testCheckboxColors() {
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
    TestCase.assertTrue(msgColorsDontMatch(lightColor, filename, colorName, false), lightSvg.contains(lightColor))
    TestCase.assertTrue(msgColorsDontMatch(darkColor, filename, colorName, true), darkSvg.contains(darkColor))
  }

  private fun msgColorsDontMatch(lightColor: String, filename: String, colorName: String, isDark: Boolean): String {
    val path = toPath(isDark) + filename
    val key = if (isDark) "$colorName.Dark" else colorName
    return "$lightColor is not presented in $path. Please fix the icon or update color key $key in UITheme class"
  }

  fun loadIconText(isDark: Boolean, filename: String): String? {
    return this.javaClass.getResourceAsStream(toPath(isDark) + filename)?.bufferedReader()?.readText()
  }
}