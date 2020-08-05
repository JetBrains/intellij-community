// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClickListener
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object GHPRReviewThreadCommentsPanel {

  private const val FOLD_THRESHOLD = 3

  fun create(commentsModel: ListModel<GHPRReviewCommentModel>,
             commentComponentFactory: (GHPRReviewCommentModel) -> JComponent): JComponent {

    if (commentsModel.size < 1) throw IllegalStateException("Thread cannot be empty")

    val panel = JPanel(VerticalLayout(UI.scale(8))).apply {
      isOpaque = false
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    Controller(commentsModel, panel, commentComponentFactory)

    return panel
  }

  private class Controller(private val model: ListModel<GHPRReviewCommentModel>,
                           private val panel: JPanel,
                           private val componentFactory: (GHPRReviewCommentModel) -> JComponent) {

    private val foldModel = SingleValueModel(true)
    private val unfoldButtonPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyLeft(30)

      addToLeft(UnfoldButton(foldModel).apply {
        foreground = UIUtil.getLabelForeground()
        font = UIUtil.getButtonFont()
      })
    }

    init {
      model.addListDataListener(object : ListDataListener {
        override fun intervalRemoved(e: ListDataEvent) {
          if (e.index0 == 0) throw IllegalStateException("Thread cannot be empty")
          for (i in e.index1 downTo e.index0) {
            panel.remove(i + 1)
          }
          updateFolding()
          panel.revalidate()
          panel.repaint()
        }

        override fun intervalAdded(e: ListDataEvent) {
          for (i in e.index0..e.index1) {
            panel.add(componentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i + 1)
          }
          panel.validate()
          panel.repaint()
        }

        override fun contentsChanged(e: ListDataEvent) {
          panel.revalidate()
          panel.repaint()
        }
      })
      foldModel.addValueChangedListener { updateFolding() }

      panel.add(componentFactory(model.getElementAt(0)), VerticalLayout.FILL_HORIZONTAL)
      panel.add(unfoldButtonPanel)

      for (i in 1 until model.size) {
        panel.add(componentFactory(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL)
      }
      updateFolding()
    }

    private fun updateFolding() {
      val shouldFold = foldModel.value && model.size > FOLD_THRESHOLD
      unfoldButtonPanel.isVisible = shouldFold

      val lastComponentIdx = panel.componentCount - 1
      for (i in 2 until lastComponentIdx) {
        panel.getComponent(i).isVisible = !shouldFold
      }
      if (shouldFold) {
        panel.getComponent(lastComponentIdx).isVisible = true
      }
    }
  }

  private class UnfoldButton(model: SingleValueModel<Boolean>) : JComponent() {
    init {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          model.value = !model.value
          return true
        }
      }.installOn(this)
    }

    override fun getPreferredSize(): Dimension {
      return Dimension(JBUI.scale(30), font.size)
    }

    override fun paintComponent(g: Graphics) {
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)

      val g2 = g as Graphics2D

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      val arc = DarculaUIUtil.BUTTON_ARC.float
      g2.color = background
      g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc))

      g2.color = foreground
      g2.font = font
      val line = StringUtil.ELLIPSIS
      val lineBounds = g2.fontMetrics.getStringBounds(line, g2)
      val x = (rect.width - lineBounds.width) / 2
      val y = (rect.height + lineBounds.y) / 2 - lineBounds.y / 2
      g2.drawString(line, x.toFloat(), y.toFloat())
    }
  }
}