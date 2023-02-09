// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.VerticalListPanel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object GHPRReviewThreadsPanel {

  fun create(model: ListModel<GHPRReviewThreadModel>, threadComponentFactory: (GHPRReviewThreadModel) -> JComponent): JComponent {
    val panel = VerticalListPanel()
    Controller(model, panel, threadComponentFactory)
    return panel
  }

  private class Controller(private val model: ListModel<GHPRReviewThreadModel>,
                           private val panel: JPanel,
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
            panel.add(threadComponentFactory(model.getElementAt(i)), i)
          }
          updateVisibility()
          panel.revalidate()
          panel.repaint()
        }

        override fun contentsChanged(e: ListDataEvent) {
          updateVisibility()
          panel.validate()
          panel.repaint()
        }
      })

      for (i in 0 until model.size) {
        panel.add(threadComponentFactory(model.getElementAt(i)), i)
      }
      updateVisibility()
    }

    private fun updateVisibility() {
      panel.isVisible = panel.componentCount > 0
    }
  }
}