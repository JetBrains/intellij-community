// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemComponentFactory.Item
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.ListModel

class GHPRTimelineComponent(private val detailsModel: SingleValueModel<GHPullRequestShort>,
                            model: ListModel<GHPRTimelineItem>,
                            itemComponentFactory: GHPRTimelineItemComponentFactory)
  : TimelineComponent<GHPRTimelineItem>(model, itemComponentFactory, itemComponentFactory.createComponent(detailsModel.value)) {

  private val timeLineColor = JBColor(ColorUtil.fromHex("#F2F2F2"), ColorUtil.fromHex("#3E3E3E"))
  private val timeLineValues = JBValue.JBValueGroup()
  private val timeLineGap = timeLineValues.value(UIUtil.DEFAULT_VGAP.toFloat())
  private val timeLineWidth = timeLineValues.value(2f)
  private val timeLineX = timeLineValues.value(20f / 2 - 1)

  init {
    border = JBUI.Borders.emptyTop(6)

    detailsModel.addValueChangedListener {
      remove(0)
      add(itemComponentFactory.createComponent(detailsModel.value), VerticalLayout.FILL_HORIZONTAL, 0)
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