// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.ui.UISettings
import training.util.DataLoader
import training.util.openLinkInBrowser
import training.util.wrapWithUrlPanel
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class ModulesPanel : JPanel() {
  private val modulesPanel = LearningItems()

  init {
    layout = BorderLayout()
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
    add(modulesPanel, BorderLayout.CENTER)
    addFooter()
  }

  private fun addFooter() {
    val linkForFeedback = LangManager.getInstance().getLangSupport()?.langCourseFeedback ?: return

    val footerContent = JPanel()
    footerContent.isOpaque = false
    footerContent.layout = VerticalLayout(4)
    footerContent.add(Box.createVerticalStrut(8))
    val linkLabel = LinkLabel<Any>(LearnBundle.message("feedback.link.text"), null) { _, _ ->
      openLinkInBrowser(linkForFeedback)
    }
    footerContent.add(linkLabel.wrapWithUrlPanel())
    footerContent.add(JLabel(LearnBundle.message("feedback.link.hint")).also {
      it.foreground = UISettings.instance.moduleProgressColor
      it.font = it.font.deriveFont(it.font.size2D - 1)
    })

    val shiftedFooter = JPanel()
    shiftedFooter.name = "footerModulePanel"
    shiftedFooter.layout = HorizontalLayout(1)
    shiftedFooter.isFocusable = false
    shiftedFooter.isOpaque = false
    shiftedFooter.border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)

    shiftedFooter.add(footerContent)
    shiftedFooter.add(Box.createHorizontalGlue())

    add(shiftedFooter, BorderLayout.PAGE_END)
  }

  fun updateMainPanel() {
    modulesPanel.removeAll()
    initModulesPanel()
  }
}

