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
  val northInset: Int by lazy { JBUI.scale(24) }
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
  val defaultTextColor = JBColor(Color(30, 30, 30), Color(208, 208, 208))
  val lessonLinkColor = JBColor(Color(17, 96, 166), Color(104, 159, 220))
  val shortcutTextColor = JBColor(Color(12, 12, 12), Color(200, 200, 200))
  val separatorColor = JBColor(Color(204, 204, 204), Color(149, 149, 149))
  val shortcutBackgroundColor = JBColor(Color(0xE6EEF7), Color(39, 43, 46))
  val codeForegroundColor = JBColor.namedColor("ParameterInfo.foreground", JBColor(0x1D1D1D, 0xBBBBBB))
  val codeBorderColor = JBColor.namedColor("Button.disabledBorderColor", JBColor(0xC4C4C4, 0x5E6060))
  val inactiveColor = JBColor(Color(200, 200, 200), Color(103, 103, 103))
  val moduleProgressColor = JBColor(0x808080, 0x8C8C8C)
  val backgroundColor = UIUtil.getTreeBackground()
  val completedColor = JBColor(0x368746, 0x50A661)
  val activeTaskBorder: Color = JBUI.CurrentTheme.Component.FOCUSED_BORDER_COLOR

  val tooltipBackgroundColor: Color = JBColor(0x1071E8, 0x0E62CF)
  val tooltipTextColor: Color = Color(0xF5F5F5)
  val tooltipCodeBackgroundColor: Color = JBColor(0x0D5CBD, 0x0250B0)

  val futureTaskNumberColor: Color = JBColor(0xDEDEDE, 0x777777)
  val activeTaskNumberColor: Color = JBColor(0x808080, 0xFEFEFE)
  val tooltipTaskNumberColor: Color = JBColor(0x6CA6ED, 0x6A9DDE)

  //BORDERS
  val emptyBorder: Border
    get() = EmptyBorder(northInset, westInset, southInset, eastInset)

  val checkmarkShiftBorder: Border
    get() = EmptyBorder(0, checkIndent, 0, 0)

  val checkIndent: Int get() = JBUI.scale(40)

  val numberTaskIndent: Int get() = JBUI.scale(11)

  companion object {
    val instance: UISettings
      get() = ApplicationManager.getApplication().getService(UISettings::class.java)
  }
}
