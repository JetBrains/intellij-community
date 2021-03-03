// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.welcomeScreen

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.HeightLimitedPane
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import org.jetbrains.annotations.NonNls
import training.dsl.LessonUtil
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.ui.UISettings
import training.util.resetPrimaryLanguage
import training.util.rigid
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.MatteBorder

open class OnboardingLessonPromoter(@NonNls private val lessonId: String) : StartPagePromoter {
  override fun needToHideSingleProject(path: String): Boolean {
    val langSupport = LangManager.getInstance().getLangSupport() ?: return false
    return LangManager.getInstance().getLearningProjectPath(langSupport) == path
  }

  override fun getPromotionForInitialState(): JPanel? {
    val rPanel: JPanel = NonOpaquePanel()
    rPanel.layout = BoxLayout(rPanel, BoxLayout.PAGE_AXIS)
    rPanel.border = JBUI.Borders.empty(10, 32)

    val vPanel: JPanel = NonOpaquePanel()
    vPanel.layout = BoxLayout(vPanel, BoxLayout.PAGE_AXIS)
    vPanel.alignmentY = Component.TOP_ALIGNMENT

    val header = JLabel(LearnBundle.message("welcome.promo.header"))
    header.font = UIUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(UIUtil.getLabelFont().size2D + 4)
    vPanel.add(header)
    vPanel.add(rigid(0, 4))
    val text = LearnBundle.message("welcome.promo.description", LessonUtil.productName)
    val heightLimitedPane = HeightLimitedPane(text, -1, UIUtil.getContextHelpForeground() as JBColor)
    heightLimitedPane.preferredSize
    vPanel.add(heightLimitedPane)
    val jButton = JButton()
    jButton.isOpaque = false
    jButton.action = object : AbstractAction(LearnBundle.message("welcome.promo.start.tour")) {
      override fun actionPerformed(e: ActionEvent?) {
        val lesson = CourseManager.instance.lessonsForModules.find { it.id == lessonId }
        if (lesson == null) {
          logger<OnboardingLessonPromoter>().error("No lesson with id $lessonId")
          return
        }
        val primaryLanguage = lesson.module.primaryLanguage
        resetPrimaryLanguage(primaryLanguage)
        OpenLessonActivities.openOnboardingFromWelcomeScreen(lesson)
      }
    }
    vPanel.add(rigid(0, 18))
    vPanel.add(buttonPixelHunting(jButton))

    val hPanel: JPanel = NonOpaquePanel()
    hPanel.layout = BoxLayout(hPanel, BoxLayout.X_AXIS)
    hPanel.add(vPanel)
    hPanel.add(Box.createHorizontalGlue())
    hPanel.add(rigid(54, 0))
    val picture = JLabel(FeaturesTrainerIcons.Img.PluginIcon)
    picture.alignmentY = Component.TOP_ALIGNMENT
    hPanel.add(picture)

    rPanel.add(NonOpaquePanel().apply {
      border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)
    })
    rPanel.add(rigid(0, 20))
    rPanel.add(hPanel)
    return rPanel
  }

  private fun buttonPixelHunting(button: JButton): JPanel {

    val buttonSizeWithoutInsets = Dimension(button.preferredSize.width - button.insets.left - button.insets.right,
                                            button.preferredSize.height - button.insets.top - button.insets.bottom)

    val buttonPlace = JPanel().apply {
      layout = null
      maximumSize = buttonSizeWithoutInsets
      preferredSize = buttonSizeWithoutInsets
      minimumSize = buttonSizeWithoutInsets
      isOpaque = false
      alignmentX = JPanel.LEFT_ALIGNMENT
    }

    buttonPlace.add(button)
    button.bounds = Rectangle(-button.insets.left, -button.insets.top, button.preferredSize.width, button.preferredSize.height)

    return buttonPlace
  }
}