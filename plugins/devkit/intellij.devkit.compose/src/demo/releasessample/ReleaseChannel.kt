package com.intellij.devkit.compose.demo.releasessample

import com.intellij.ui.JBColor
import java.awt.Color

@Suppress("UnregisteredNamedColor") // They exist at runtime
internal enum class ReleaseChannel(val background: Color, val foreground: Color) {
  Stable(
    background =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Green10", 0xE6F7E9),
        // dark  =
        JBColor.namedColor("ColorPalette.Green3", 0x436946),
      ),
    foreground =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Green5", 0x369650),
        // dark  =
        JBColor.namedColor("ColorPalette.Green6", 0x5FAD65),
      ),
  ),
  Beta(
    background =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Yellow10", 0xFCEBA4),
        // dark  =
        JBColor.namedColor("ColorPalette.Yellow3", 0x826A41),
      ),
    foreground =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Yellow4", 0xFFAF0F),
        // dark  =
        JBColor.namedColor("ColorPalette.Yellow6", 0xD6AE58),
      ),
  ),
  Canary(
    background =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Orange8", 0xEC8F4C),
        // dark  =
        JBColor.namedColor("ColorPalette.Orange3", 0x825845),
      ),
    foreground =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Orange5", 0xEC8F4C),
        // dark  =
        JBColor.namedColor("ColorPalette.Orange6", 0xE08855),
      ),
  ),
  Other(
    background =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Gray12", 0xEBECF0),
        // dark  =
        JBColor.namedColor("ColorPalette.Gray5", 0x4E5157),
      ),
    foreground =
      JBColor(
        // regular =
        JBColor.namedColor("ColorPalette.Gray6", 0x6C707E),
        // dark  =
        JBColor.namedColor("ColorPalette.Gray10", 0xB4B8BF),
      ),
  ),
}
