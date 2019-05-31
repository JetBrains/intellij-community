// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ClickListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileComment
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThread
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GithubPullRequestEditorCommentsThreadComponentFactoryImpl
internal constructor(private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : GithubPullRequestEditorCommentsThreadComponentFactory {

  override fun createComponent(thread: GithubPullRequestFileCommentsThread): JComponent {
    val threadPanel = RoundedPanel(VerticalFlowLayout(JBUI.scale(UIUtil.DEFAULT_HGAP), JBUI.scale(UIUtil.DEFAULT_VGAP))).apply {
      border = BorderFactory.createCompoundBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0),
                                                  IdeBorderFactory.createRoundedBorder(10, 1))
    }

    ThreadPanelController(thread, threadPanel)
    return threadPanel
  }

  private class RoundedPanel(layoutManager: LayoutManager) : JPanel(layoutManager) {
    init {
      isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
      GraphicsUtil.setupRoundedBorderAntialiasing(g)

      val g2 = g as Graphics2D
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)
      g2.color = background
      g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 10f, 10f))
    }
  }

  private fun createComponent(avatarsProvider: CachingGithubAvatarIconsProvider, comment: GithubPullRequestFileComment): JComponent {
    return JPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill())

      val avatarIcon = avatarsProvider.getIcon(comment.authorAvatarUrl)
      val avatar = JLabel(avatarIcon).apply {
        border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP)
      }
      add(avatar, CC().spanY(2).alignY("top"))

      val username = LinkLabel.create(comment.authorUsername) {
        BrowserUtil.browse(comment.authorLinkUrl)
      }.apply {
        border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP)
      }
      add(username, CC())

      val date = JLabel(DateFormatUtil.formatPrettyDate(comment.dateCreated)).apply {
        foreground = UIUtil.getContextHelpForeground()
      }
      add(date, CC().growX().pushX().wrap())

      val textPane = object : HtmlPanel() {
        init {
          isOpaque = false
          border = JBUI.Borders.empty()
          editorKit = UIUtil.JBWordWrapHtmlEditorKit()
          update()

          putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
        }

        override fun getBody() = comment.body

        override fun getBodyFont(): Font = UIUtil.getLabelFont()
      }

      add(textPane, CC().spanX(3).growX().minWidth("0").minHeight("0"))
    }
  }

  private fun createUnfoldButton(): JComponent {
    return object : JComponent() {
      init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = JBDimension(30, UIUtil.getLabelFont().size, true)
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
        val lineBounds = g2.fontMetrics.getStringBounds("...", g2)
        val x = (rect.width - lineBounds.width) / 2
        val y = (rect.height + lineBounds.y) / 2 - lineBounds.y / 2
        g2.drawString("...", x.toFloat(), y.toFloat())
      }
    }
  }

  private inner class ThreadPanelController(private val thread: GithubPullRequestFileCommentsThread, private val panel: JPanel) {
    private val avatarsProvider = avatarIconsProviderFactory.create(JBValue.UIInteger("GitHub.Avatar.Size", 20), panel)

    private val collapseThreshold = 2
    private val unfoldButtonPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyLeft(30)

      addToLeft(createUnfoldButton().apply {
        object : ClickListener() {
          override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            thread.fold = !thread.fold
            return true
          }
        }.installOn(this)
      })
    }

    init {
      panel.add(createComponent(avatarsProvider, thread.items.first()))
      panel.add(unfoldButtonPanel)

      for (i in 1 until thread.items.size) {
        panel.add(createComponent(avatarsProvider, thread.getElementAt(i)))
      }
      updateFolding()

      thread.addListDataListener(object : ListDataListener {
        override fun intervalRemoved(e: ListDataEvent) {
          for (i in e.index0..e.index1) {
            panel.remove(i + 1)
          }
          updateFolding()
        }

        override fun intervalAdded(e: ListDataEvent) {
          for (i in e.index0..e.index1) {
            panel.add(createComponent(avatarsProvider, thread.getElementAt(i)), i + 1)
          }
          updateFolding()
        }

        override fun contentsChanged(e: ListDataEvent) {
          for (i in e.index0..e.index1) {
            val idx = if (i == 0) i else i + 1
            panel.remove(idx)
            panel.add(createComponent(avatarsProvider, thread.getElementAt(i)), idx)
          }
        }
      })
      thread.addFoldStateChangeListener { updateFolding() }
    }

    private fun updateFolding() {
      val shouldFold = thread.fold && thread.size > collapseThreshold
      unfoldButtonPanel.isVisible = shouldFold
      for (i in 2 until panel.componentCount - 1) {
        panel.getComponent(i).isVisible = !shouldFold
      }
      if (shouldFold) {
        panel.getComponent(panel.componentCount - 1).isVisible = true
      }
    }
  }
}