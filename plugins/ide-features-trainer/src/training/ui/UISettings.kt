// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

@Suppress("MemberVisibilityCanBePrivate")
internal class UISettings {

  //GENERAL UI SETTINGS
  val width: Int by lazy { JBUI.scale(500) }

  //MAIN INSETS
  val northInset: Int by lazy { JBUI.scale(20) }
  val westInset: Int by lazy { JBUI.scale(24) }
  val southInset: Int by lazy { JBUI.scale(24) }
  val eastInset: Int by lazy { JBUI.scale(24) }

  val verticalModuleItemInset: Int by lazy { JBUI.scale(8) }

  //GAPS
  val progressModuleGap: Int by lazy { JBUI.scale(2) }
  val expandAndModuleGap: Int by lazy { JBUI.scale(10) }

  //FONTS
  val plainFont: Font
    get() = JBUI.Fonts.label()
  val fontSize: Float
    get() = plainFont.size2D
  val modulesFont: Font
    get() = plainFont.deriveFont(Font.BOLD)

  fun getFont(relatedUnscaledSize: Int): Font = plainFont.deriveFont(fontSize + JBUI.scale(relatedUnscaledSize))

  //COLORS
  val defaultTextColor: Color = JBUI.CurrentTheme.Label.foreground()
  val lessonLinkColor: Color = JBUI.CurrentTheme.Link.Foreground.ENABLED
  val shortcutTextColor: Color = defaultTextColor
  val separatorColor: Color = JBColor.namedColor("Button.startBorderColor", 0xC4C4C4, 0x5E6060)
  val shortcutBackgroundColor: Color = JBColor.namedColor("Lesson.shortcutBackground", 0xE6EEF7, 0x333638)
  val codeForegroundColor: Color = defaultTextColor
  val codeBorderColor: Color = JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false)
  val inactiveColor: Color = defaultTextColor.addAlpha(0.3)
  val moduleProgressColor: Color = JBColor.namedColor("Label.infoForeground", 0x808080, 0x8C8C8C)
  val backgroundColor: Color = UIUtil.getTreeBackground()
  val completedColor: Color = JBColor.namedColor("Label.successForeground", 0x368746, 0x50A661)
  val activeTaskBorder: Color = JBColor.namedColor("Component.focusColor", 0x97C3F3, 0x3D6185)

  val tooltipBackgroundColor: Color = JBColor.namedColor("Lesson.Tooltip.background",0x1071E8, 0x0E62CF)
  val tooltipButtonBackgroundColor: Color = JBColor.namedColor("Lesson.Tooltip.spanBackground", 0x0D5CBD, 0x0250B0)
  val tooltipTextColor: Color = JBColor.namedColor("Lesson.Tooltip.foreground",0xF5F5F5)

  val activeTaskNumberColor: Color = JBColor.namedColor("Lesson.stepNumberForeground",0x808080, 0xFEFEFE)
  val futureTaskNumberColor: Color = activeTaskNumberColor.addAlpha(0.3)
  val tooltipTaskNumberColor: Color = JBColor.namedColor("Lesson.Tooltip.stepNumberForeground",0x6CA6ED, 0x6A9DDE)

  //BORDERS
  val emptyBorder: Border
    get() = EmptyBorder(northInset, westInset, southInset, eastInset)

  val lessonHeaderBorder: Border
    get() = EmptyBorder(0, JBUI.scale(14), 0, JBUI.scale(14))

  val checkmarkShiftBorder: Border
    get() = EmptyBorder(0, checkIndent, 0, 0)

  val balloonAdditionalBorder: EmptyBorder
    get() = EmptyBorder(JBUI.scale(7), JBUI.scale(4), JBUI.scale(7), 0)

  val checkIndent: Int get() = JBUI.scale(40)

  val numberTaskIndent: Int get() = JBUI.scale(11)

  val balloonIndent: Int get() = JBUI.scale(27)

  companion object {
    val instance: UISettings
      get() = ApplicationManager.getApplication().getService(UISettings::class.java)

    private fun Color.addAlpha(alpha: Double) : Color {
      return JBColor { Color(red, green, blue, (255*alpha).toInt()) }
    }
  }
}
