// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UI
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRReviewThreadsPanel(model: GHPRReviewThreadsModel, private val threadComponentFactory: (GHPRReviewThreadModel) -> JComponent)
  : JPanel(VerticalLayout(UI.scale(12))), ComponentWithEmptyText {

  private val statusText = object : StatusText(this) {
    init {
      text = ApplicationBundle.message("label.loading.page.please.wait")
    }

    override fun isStatusVisible(): Boolean = model.isEmpty
  }

  init {
    isOpaque = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          remove(i)
        }
        revalidate()
        repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          add(threadComponentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
        }
        revalidate()
        repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        validate()
        repaint()
      }
    })

    for (i in 0 until model.size) {
      add(threadComponentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
    }
  }

  override fun getEmptyText() = statusText

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    statusText.paint(this, g)
  }
}