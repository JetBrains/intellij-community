// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClickListener
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRReviewThreadPanel(private val avatarsProvider: CachingGithubAvatarIconsProvider,
                            private val thread: GHPRReviewThreadModel)
  : JPanel(VerticalFlowLayout(0, JBUI.scale(10))) {

  private val collapseThreshold = 2
  private val unfoldButtonPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.emptyLeft(30)

    addToLeft(UnfoldButton(thread))
  }

  init {
    isOpaque = false

    thread.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          remove(i + 1)
        }
        updateFolding()
        revalidate()
        repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          add(GHPRReviewCommentComponent(avatarsProvider, thread.getElementAt(i)), i + 1)
        }
        updateFolding()
        validate()
        repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        revalidate()
        repaint()
      }
    })
    thread.addFoldStateChangeListener { updateFolding() }

    add(GHPRReviewCommentComponent(avatarsProvider, thread.items.first()))
    add(unfoldButtonPanel)

    for (i in 1 until thread.items.size) {
      add(GHPRReviewCommentComponent(avatarsProvider, thread.getElementAt(i)))
    }
    updateFolding()

    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
  }

  private fun updateFolding() {
    val shouldFold = thread.fold && thread.size > collapseThreshold
    unfoldButtonPanel.isVisible = shouldFold
    for (i in 2 until componentCount - 1) {
      getComponent(i).isVisible = !shouldFold
    }
    if (shouldFold) {
      getComponent(componentCount - 1).isVisible = true
    }
  }

  override fun remove(index: Int) {
    if (index == 0) throw IllegalArgumentException("Can't remove root comment")
    if (index == 1) throw IllegalArgumentException("Can't unfold button")
    super.remove(index)
  }

  companion object {
    private class UnfoldButton(thread: GHPRReviewThreadModel) : JComponent() {
      init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        object : ClickListener() {
          override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            thread.fold = !thread.fold
            return true
          }
        }.installOn(this)
        updateSize()
      }

      override fun updateUI() {
        super.updateUI()
        updateSize()
      }

      private fun updateSize() {
        preferredSize = Dimension(JBUI.scale(30), UIUtil.getLabelFont().size)
      }

      override fun paintComponent(g: Graphics) {
        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, insets)

        val g2 = g as Graphics2D

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

        val arc = DarculaUIUtil.BUTTON_ARC.float
        g2.color = UIUtil.getPanelBackground()
        g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc))

        g2.color = UIUtil.getLabelForeground()
        g2.font = UIUtil.getLabelFont()
        val line = StringUtil.ELLIPSIS
        val lineBounds = g2.fontMetrics.getStringBounds(line, g2)
        val x = (rect.width - lineBounds.width) / 2
        val y = (rect.height + lineBounds.y) / 2 - lineBounds.y / 2
        g2.drawString(line, x.toFloat(), y.toFloat())
      }
    }
  }
}