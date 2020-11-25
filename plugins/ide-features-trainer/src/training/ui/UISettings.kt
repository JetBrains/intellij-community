// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.JButton
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import kotlin.reflect.KProperty1

@Suppress("MemberVisibilityCanBePrivate")
class UISettings {

  //GENERAL UI SETTINGS
  val width: Int by lazy { JBUI.scale(500) }

  //MAIN INSETS
  val northInset: Int by lazy { JBUI.scale(24) }
  val westInset: Int by lazy { JBUI.scale(19) }
  val southInset: Int by lazy { JBUI.scale(32) }
  val eastInset: Int by lazy { JBUI.scale(32) }
  val checkWidth: Int by lazy { FeaturesTrainerIcons.Img.Checkmark.iconWidth }
  val checkRightIndent: Int by lazy { 5 }

  //GAPS
  val headerGap: Int by lazy { JBUI.scale(2) }
  val moduleGap: Int by lazy { JBUI.scale(20) }
  val descriptionGap: Int by lazy { JBUI.scale(100) }
  val progressGap: Int by lazy { JBUI.scale(12) }
  val lessonGap: Int by lazy { JBUI.scale(12) }
  val radioButtonGap: Int by lazy { JBUI.scale(3) }

  val lessonNameGap: Int by lazy { JBUI.scale(5) }
  val beforeButtonGap: Int by lazy { JBUI.scale(24) }
  val afterButtonGap: Int by lazy { JBUI.scale(44) }
  val afterCaptionGap: Int by lazy { JBUI.scale(12) }
  val groupGap: Int by lazy { JBUI.scale(24) }
  val moduleNameSeparatorGap: Int by lazy { JBUI.scale(5) }
  val moduleNameLessonsGap: Int by lazy { JBUI.scale(10) }
  val moduleNameLessonGap: Int by lazy { JBUI.scale(32) }
  val languagePanelButtonsGap: Int by lazy { JBUI.scale(8) }

  //FONTS
  val fontSize: Float
    get() = JBUI.Fonts.label().size2D
  //TODO: remove in 2021.1
  val fontFace: String
    get() = JBUI.Fonts.label().fontName
  val modulesFont: Font
    get() = JBUI.Fonts.label().deriveFont(Font.BOLD)
  val moduleNameFont: Font
    get() = JBUI.Fonts.label().deriveFont(fontSize + 1)
  val plainFont: Font
    get() = JBUI.Fonts.label()
  val italicFont: Font
    get() = plainFont.deriveFont(Font.ITALIC)
  val boldFont: Font
    get() = plainFont.deriveFont(Font.BOLD)
  val lessonHeaderFont: Font
    get() = JBUI.Fonts.label().deriveFont(fontSize + 5).deriveFont(Font.BOLD)
  val helpHeaderFont: Font
    get() = JBUI.Fonts.label().deriveFont(fontSize + 1).deriveFont(Font.BOLD)

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
  val activeTaskBorder = JBColor.namedColor("Component.focusedBorderColor", JBColor(0x87AFDA, 0x466d94))

  //BORDERS
  val emptyBorder: Border
    get() = EmptyBorder(northInset, westInset, southInset, eastInset)
  val emptyBorderWithNoEastHalfNorth: Border
    get() = EmptyBorder(northInset / 2, westInset, southInset, 0)
  val eastBorder: Border
    get() = EmptyBorder(0, 0, 0, eastInset)
  val smallEastBorder: Border
    get() = EmptyBorder(0, 0, 0, eastInset / 4)

  val radioButtonBorder: Border
    get() = EmptyBorder(radioButtonGap, 0, radioButtonGap, 0)

  val checkmarkShiftBorder: Border
    get() = EmptyBorder(0, checkIndent, 0, 0)

  val checkmarkShiftButtonBorder: Border by lazy {
    return@lazy EmptyBorder(0, checkIndent - JButton().insets.left, 0, 0)
  }

  val checkIndent: Int
    get() = checkWidth + checkRightIndent

  companion object {
    val instance: training.ui.UISettings
      get() = ApplicationManager.getApplication().getService(training.ui.UISettings::class.java)

    fun rigidGap(gapName: String, gapSize: Int, isVertical: Boolean = true): Box.Filler {
      val rigidArea = if (isVertical) Box.createRigidArea(Dimension(0, gapSize)) else Box.createRigidArea(Dimension(gapSize, 0))
      rigidArea.name = gapName
      return rigidArea as Box.Filler
    }

    fun rigidGap(settingsValue: KProperty1<training.ui.UISettings, Int>, isVertical: Boolean = true): Box.Filler {
      val value = settingsValue.get(instance)
      return rigidGap(settingsValue.name, value, isVertical)
    }
  }

}

