// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import java.awt.Color
import java.awt.Font
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

@Suppress("MemberVisibilityCanBePrivate")
class UISettings {

  //GENERAL UI SETTINGS
  val width: Int by lazy { JBUI.scale(500) }

  //MAIN INSETS
  val northInset: Int by lazy { JBUI.scale(24) }
  val westInset: Int by lazy { JBUI.scale(24) }
  val southInset: Int by lazy { JBUI.scale(24) }
  val eastInset: Int by lazy { JBUI.scale(24) }

  val verticalModuleItemInset: Int by lazy { JBUI.scale(8) }

  //GAPS
  val progressCourseGap: Int by lazy { JBUI.scale(4) }
  val progressModuleGap: Int by lazy { JBUI.scale(2) }
  val expandAndModuleGap: Int by lazy { JBUI.scale(10) }
  val radioButtonGap: Int by lazy { JBUI.scale(3) }

  val groupGap: Int by lazy { JBUI.scale(24) }
  val languagePanelButtonsGap: Int by lazy { JBUI.scale(8) }

  //FONTS
  val plainFont: Font
    get() = JBUI.Fonts.label()
  val fontSize: Float
    get() = plainFont.size2D
  val modulesFont: Font
    get() = plainFont.deriveFont(Font.BOLD)
  val boldFont: Font
    get() = plainFont.deriveFont(Font.BOLD)

  fun getFont(relatedUnscaledSize: Int): Font = plainFont.deriveFont(fontSize + JBUI.scale(relatedUnscaledSize))

  //COLORS
  val defaultTextColor = JBColor(Color(30, 30, 30), Color(208, 208, 208))
  val lessonActiveColor = JBColor(Color(0, 0, 0), Color(202, 202, 202))
  val lessonLinkColor = JBColor(Color(17, 96, 166), Color(104, 159, 220))
  val shortcutTextColor = JBColor(Color(12, 12, 12), Color(200, 200, 200))
  val separatorColor = JBColor(Color(204, 204, 204), Color(149, 149, 149))
  val shortcutBackgroundColor = JBColor(Color(0xE6EEF7), Color(39, 43, 46))
  val codeForegroundColor = JBColor.namedColor("ParameterInfo.foreground", JBColor(0x1D1D1D, 0xBBBBBB))
  val codeBorderColor = JBColor.namedColor("Button.disabledBorderColor", JBColor(0xC4C4C4, 0x5E6060))
  val passedColor = JBColor(Color(200, 200, 200), Color(103, 103, 103))
  val moduleProgressColor = JBColor.namedColor("infoPanelForeground", JBColor(0x808080, 0x8C8C8C))
  val backgroundColor = UIUtil.getTreeBackground()
  val descriptionColor = Color(128, 128, 128)
  val completedColor = JBColor(0x368746, 0x50A661)
  var questionColor = lessonActiveColor
  val activeTaskBorder = JBUI.CurrentTheme.Component.FOCUSED_BORDER_COLOR

  //BORDERS
  val emptyBorder: Border
    get() = EmptyBorder(northInset, westInset, southInset, eastInset)

  val radioButtonBorder: Border
    get() = EmptyBorder(radioButtonGap, 0, radioButtonGap, 0)

  val checkmarkShiftBorder: Border
    get() = EmptyBorder(0, checkIndent, 0, 0)

  val checkIndent: Int
    get() = JBUI.scale(FeaturesTrainerIcons.Img.Checkmark.iconWidth + 5)

  companion object {
    val instance: UISettings
      get() = ApplicationManager.getApplication().getService(UISettings::class.java)
  }
}

