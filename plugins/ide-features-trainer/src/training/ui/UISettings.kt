// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.JLabel
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
  val beforeButtonGap: Int by lazy { JBUI.scale(20) }
  val afterButtonGap: Int by lazy { JBUI.scale(44) }
  val afterCaptionGap: Int by lazy { JBUI.scale(12) }
  val groupGap: Int by lazy { JBUI.scale(24) }
  val moduleNameSeparatorGap: Int by lazy { JBUI.scale(5) }
  val moduleNameLessonsGap: Int by lazy { JBUI.scale(10) }
  val moduleNameLessonGap: Int by lazy { JBUI.scale(32) }
  val languagePanelButtonsGap: Int by lazy { JBUI.scale(8) }

  //FONTS
  val fontSize: Float by lazy { UISettings.instance.fontSize.ifZero(JBUI.scale(13)) * 1f }
  val fontFace: String by lazy { UISettings.instance.fontFace ?: JLabel().font.fontName }
  val moduleNameFont: Font by lazy { UIUtil.getLabelFont().deriveFont(fontSize + 1f) }
  val plainFont: Font by lazy { UIUtil.getLabelFont().deriveFont(fontSize) }
  val italicFont: Font by lazy { plainFont.deriveFont(Font.ITALIC) }
  val boldFont: Font by lazy { plainFont.deriveFont(Font.BOLD) }
  val lessonHeaderFont: Font by lazy { UIUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(JBUIScale.scale(18.0f)) }
  val helpHeaderFont: Font by lazy { UIUtil.getLabelFont().deriveFont(fontSize + 1f).deriveFont(Font.BOLD) }

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
  val backgroundColor = UIUtil.getTreeBackground()
  val descriptionColor = Color(128, 128, 128)
  val completedColor = JBColor(Color(50, 100, 50), Color(100, 150, 100))
  var questionColor = lessonActiveColor

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

  private fun Int.ifZero(nonZeroValue: Int): Int =
    if (this == 0) nonZeroValue else this
}

