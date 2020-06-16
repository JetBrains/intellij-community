// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemComponentFactory.Item
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRTimelineComponent(private val model: ListModel<GHPRTimelineItem>,
                            private val itemComponentFactory: GHPRTimelineItemComponentFactory)
  : JPanel(VerticalLayout(UI.scale(20))) {

  private val timeLineColor = JBColor(ColorUtil.fromHex("#F2F2F2"), ColorUtil.fromHex("#3E3E3E"))
  private val timeLineValues = JBValue.JBValueGroup()
  private val timeLineGap = timeLineValues.value(UIUtil.DEFAULT_VGAP.toFloat())
  private val timeLineWidth = timeLineValues.value(2f)
  private val timeLineX = timeLineValues.value(20f / 2 - 1)

  init {
    isOpaque = false
    border = JBUI.Borders.emptyTop(6)

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
          add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
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
      add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
    }
  }

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    // paint time LINE
    // painted from bottom to top
    synchronized(treeLock) {
      val lastIdx = components.indexOfLast { it.isVisible }
      if (lastIdx < 0) return
      val lastComp = getComponent(lastIdx) as? Item ?: return
      var yEnd = computeYEnd(lastComp)

      g as Graphics2D
      g.color = timeLineColor
      val x = timeLineX.float.toDouble()

      for (i in lastIdx - 1 downTo 0) {
        val comp = getComponent(i).takeIf { it.isVisible } as? Item ?: continue
        val yStart = computeYStart(comp)
        if (yStart >= yEnd) continue
        LinePainter2D.paint(g, x, yStart.toDouble(), x, yEnd.toDouble(), LinePainter2D.StrokeType.INSIDE, timeLineWidth.float.toDouble())
        yEnd = computeYEnd(comp)
      }
    }
  }

  private fun computeYStart(item: Item) = item.y +
                                          (item.marker.y + item.marker.height - item.marker.insets.bottom) +
                                          timeLineGap.get()

  private fun computeYEnd(item: Item) = item.y + (item.marker.y + item.marker.insets.top) - timeLineGap.get()
}