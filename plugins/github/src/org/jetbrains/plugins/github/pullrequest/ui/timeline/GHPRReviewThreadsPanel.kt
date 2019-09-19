// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ui.components.panels.VerticalBox
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import javax.swing.JComponent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRReviewThreadsPanel(model: GHPRReviewThreadsModel, private val threadComponentFactory: (GHPRReviewThreadModel) -> JComponent)
  : VerticalBox() {

  init {
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
          add(threadComponentFactory(model.getElementAt(i)), i)
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
      add(threadComponentFactory(model.getElementAt(i)), i)
    }
  }
}