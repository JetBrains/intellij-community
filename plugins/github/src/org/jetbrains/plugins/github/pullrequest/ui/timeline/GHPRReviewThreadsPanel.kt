// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object GHPRReviewThreadsPanel {

  fun create(model: GHPRReviewThreadsModel, threadComponentFactory: (GHPRReviewThreadModel) -> JComponent): JComponent {
    val panel = JPanel(VerticalLayout(UI.scale(12))).apply {
      isOpaque = false
    }

    val loadingPanel = JPanel(SingleComponentCenteringLayout()).apply {
      isOpaque = false
      add(JLabel(ApplicationBundle.message("label.loading.page.please.wait")).apply {
        foreground = UIUtil.getContextHelpForeground()
      })
    }

    Controller(model, panel, loadingPanel, threadComponentFactory)

    return panel
  }

  private class Controller(private val model: GHPRReviewThreadsModel,
                           private val panel: JPanel,
                           private val loadingPanel: JPanel,
                           private val threadComponentFactory: (GHPRReviewThreadModel) -> JComponent) {
    init {
      model.addListDataListener(object : ListDataListener {
        override fun intervalRemoved(e: ListDataEvent) {
          for (i in e.index1 downTo e.index0) {
            panel.remove(i)
          }
          updateVisibility()
          panel.revalidate()
          panel.repaint()
        }

        override fun intervalAdded(e: ListDataEvent) {
          for (i in e.index0..e.index1) {
            panel.add(threadComponentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
          }
          updateVisibility()
          panel.revalidate()
          panel.repaint()
        }

        override fun contentsChanged(e: ListDataEvent) {
          if (model.loaded) panel.remove(loadingPanel)
          updateVisibility()
          panel.validate()
          panel.repaint()
        }
      })

      if (!model.loaded) {
        panel.add(loadingPanel, VerticalLayout.FILL_HORIZONTAL)
      }
      else for (i in 0 until model.size) {
        panel.add(threadComponentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
      }
      updateVisibility()
    }

    private fun updateVisibility() {
      panel.isVisible = panel.componentCount > 0
    }
  }
}