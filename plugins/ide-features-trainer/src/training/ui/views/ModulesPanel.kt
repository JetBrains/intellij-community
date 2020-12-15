// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import training.learn.CourseManager
import training.ui.UISettings
import training.util.DataLoader
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class ModulesPanel : JPanel() {
  private val modulesPanel = LearningItems()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isFocusable = false
    isOpaque = true
    background = UISettings.instance.backgroundColor

    initModulesPanel()
    add(modulesPanel)
    add(Box.createVerticalGlue())

    preferredSize = Dimension(UISettings.instance.width, 100)
    border = UISettings.instance.emptyBorderWithNoEastHalfNorth

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
  }

  fun updateMainPanel() {
    modulesPanel.removeAll()
    initModulesPanel()
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(modulesPanel.minimumSize.getWidth().toInt() + (UISettings.instance.westInset + UISettings.instance.westInset),
                     modulesPanel.minimumSize.getHeight().toInt() + (UISettings.instance.northInset + UISettings.instance.southInset))
  }
}

