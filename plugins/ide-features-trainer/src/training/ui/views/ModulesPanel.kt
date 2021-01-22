// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.lang.Language
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.ui.UISettings
import training.util.*
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class ModulesPanel : JPanel() {
  private val modulesPanel = LearningItems()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isFocusable = false
    isOpaque = true
    background = UISettings.instance.backgroundColor
    border = UISettings.instance.emptyBorder

    initModulesPanel()

    revalidate()
    repaint()
  }

  private fun initModulesPanel() {
    val modules = CourseManager.instance.modules
    if (DataLoader.liveMode) {
      CourseManager.instance.clearModules()
    }

    modulesPanel.let {
      it.modules = modules
      it.updateItems(CourseManager.instance.unfoldModuleOnInit)
    }

    removeAll()
    addHeaderPanel()
    modulesPanel.alignmentX = LEFT_ALIGNMENT
    add(modulesPanel)
    add(Box.createVerticalGlue())
    addFooter()
  }

  private fun addHeaderPanel() {
    val headerContent = JPanel()
    headerContent.isOpaque = false
    headerContent.layout = BoxLayout(headerContent, BoxLayout.Y_AXIS)
    headerContent.alignmentX = LEFT_ALIGNMENT
    val langSupport = LangManager.getInstance().getLangSupport() ?: return
    val primaryLanguageId = langSupport.primaryLanguage
    val language = Language.findLanguageByID(primaryLanguageId) ?: return
    headerContent.add(JLabel(LearnBundle.message("modules.panel.header", language.displayName)).also {
      it.font = UISettings.instance.getFont(3).deriveFont(Font.BOLD)
    })
    headerContent.add(rigid(0, 4))
    headerContent.add(JLabel(learningProgressString(CourseManager.instance.lessonsForModules)).also {
      it.foreground = UISettings.instance.moduleProgressColor
    })

    headerContent.add(scaledRigid(0, UISettings.instance.northInset - UISettings.instance.verticalModuleItemInset))
    add(headerContent)
  }

  private fun addFooter() {
    val linkForFeedback = LangManager.getInstance().getLangSupport()?.langCourseFeedback ?: return

    val footerContent = JPanel()
    footerContent.isOpaque = false
    footerContent.layout = BoxLayout(footerContent, BoxLayout.Y_AXIS)
    footerContent.add(rigid(0, 15))
    val linkLabel = LinkLabel<Any>(LearnBundle.message("feedback.link.text"), null) { _, _ ->
      openLinkInBrowser(linkForFeedback)
    }
    footerContent.add(linkLabel.wrapWithUrlPanel())
    footerContent.add(rigid(0, 4))
    footerContent.add(JLabel(LearnBundle.message("feedback.link.hint")).also {
      it.foreground = UISettings.instance.moduleProgressColor
      it.font = UISettings.instance.getFont(-1)
    })

    val shiftedFooter = JPanel()
    shiftedFooter.name = "footerModulePanel"
    shiftedFooter.layout = BoxLayout(shiftedFooter, BoxLayout.X_AXIS)
    shiftedFooter.isFocusable = false
    shiftedFooter.isOpaque = false
    shiftedFooter.border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)
    shiftedFooter.alignmentX = LEFT_ALIGNMENT

    shiftedFooter.add(footerContent)
    shiftedFooter.add(rigid(1, 0))
    shiftedFooter.add(Box.createHorizontalGlue())

    add(shiftedFooter)
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(modulesPanel.minimumSize.getWidth().toInt() + UISettings.instance.westInset + UISettings.instance.eastInset,
                     super.getPreferredSize().height)
  }

  fun updateMainPanel() {
    modulesPanel.removeAll()
    initModulesPanel()
  }
}

