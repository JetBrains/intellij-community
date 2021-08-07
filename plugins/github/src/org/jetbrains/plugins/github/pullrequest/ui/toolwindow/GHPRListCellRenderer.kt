// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.OpenReviewButton
import com.intellij.collaboration.ui.codereview.OpenReviewButtonViewModel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.awt.*
import java.lang.Integer.max
import javax.swing.*
import javax.swing.plaf.ComponentUI
import kotlin.properties.Delegates.observable

class GHPRListCellRenderer(private val avatarIconsProvider: GHAvatarIconsProvider,
                           private val openButtonViewModel: OpenReviewButtonViewModel)
  : ListCellRenderer<GHPullRequestShort>, JPanel() {

  private val stateIcon = JLabel()
  private val title = JLabel()
  private val info = JLabel()
  private val labels = LabelsComponent()
  private val assignees = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.X_AXIS)
  }
  private val openButtonPanel = OpenReviewButton.createOpenReviewButton(GithubBundle.message("pull.request.open.action"))

  init {
    val gapAfter = "${JBUI.scale(5)}px"

    val infoPanel = JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(5, 8)

      layout = MigLayout(LC().gridGap(gapAfter, "0")
                           .insets("0", "0", "0", "0")
                           .fillX())

      add(stateIcon)

      add(title, CC()
        .minWidth("pref/2px")
        .pushX()
        .split(2)
        .shrinkPrioX(1))
      add(labels, CC()
        .minWidth("0px")
        .pushX()
        .shrinkPrioX(0))

      add(info, CC().newline()
        .minWidth("0px")
        .pushX()
        .skip(1))
    }


    layout = MigLayout(LC().gridGap(gapAfter, "0").noGrid()
                         .insets("0", "0", "0", "0")
                         .fillX())

    add(infoPanel, CC().minWidth("0"))
    add(assignees, CC().minWidth("0").gapBefore("push"))
    add(openButtonPanel, CC().minWidth("pref").growY())
  }

  override fun getListCellRendererComponent(list: JList<out GHPullRequestShort>,
                                            value: GHPullRequestShort,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    background = ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus())
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

    stateIcon.apply {
      icon = GHUIUtil.getPullRequestStateIcon(value.state, value.isDraft)
      toolTipText = GHUIUtil.getPullRequestStateText(value.state, value.isDraft)
    }
    title.apply {
      text = value.title
      foreground = primaryTextColor
    }
    info.apply {
      text = GithubBundle.message("pull.request.list.item.info", value.number, value.author?.login,
                                  DateFormatUtil.formatDate(value.createdAt))
      foreground = secondaryTextColor
    }
    labels.apply {
      labels = value.labels
      /*removeAll()
      for (label in value.labels) {
        add(GHUIUtil.createIssueLabelLabel(label))
        add(Box.createRigidArea(JBDimension(4, 0)))
      }*/
    }
    assignees.apply {
      removeAll()
      for (assignee in value.assignees) {
        if (componentCount != 0) {
          add(Box.createRigidArea(JBDimension(UIUtil.DEFAULT_HGAP, 0)))
        }
        add(JLabel().apply {
          icon = avatarIconsProvider.getIcon(assignee.avatarUrl)
          toolTipText = assignee.login
        })
      }
    }
    openButtonPanel.apply {
      isVisible = index == openButtonViewModel.hoveredRowIndex
      isOpaque = openButtonViewModel.isButtonHovered
    }

    return this
  }

  private class LabelsComponent : JComponent() {
    var labels by observable(emptyList<GHLabel>()) { _, _, _ ->
      repaint()
    }

    init {
      setUI(LabelsComponentUI())
    }
  }

  private class LabelsComponentUI : ComponentUI() {

    private val gap = JBUI.uiIntValue("LabelsComponent.Gap", 4)

    override fun installUI(c: JComponent) {
      c.isOpaque = false
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, c)
    }

    override fun paint(g: Graphics, c: JComponent) {
      c as LabelsComponent

      val g2d = g.create() as Graphics2D
      try {
        val r = Rectangle(c.getSize())
        JBInsets.removeFrom(r, c.insets)

        g2d.font = c.font
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

        if (c.isOpaque) {
          g2d.color = c.background
          g2d.fill(r)
        }

        var x = r.x
        for (label in c.labels) {
          val fm = c.getFontMetrics(c.font)
          val size = getLabelSize(label, fm)

          val background = GHUIUtil.getLabelBackground(label)
          g2d.color = background
          g2d.fillRect(x, r.y, size.width, size.height)

          val foreground = GHUIUtil.getLabelForeground(background)
          g2d.color = foreground
          g2d.drawString(getLabelText(label), x, size.height - fm.maxDescent)

          // adding a redundant gap after last label, but by the time we painted it we no longer care about X
          x += size.width + gap.get()
        }
      }
      finally {
        g2d.dispose()
      }
    }

    override fun getMinimumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }

    override fun getPreferredSize(c: JComponent): Dimension {
      val labels = (c as LabelsComponent).labels
      if (labels.isEmpty()) return Dimension(0, 0)

      val fm = c.getFontMetrics(c.font)
      val dim = Dimension(0, 0)
      for (label in labels) {
        val labelSize = getLabelSize(label, fm)
        dim.width = dim.width + labelSize.width
        dim.height = max(dim.height, labelSize.height)
      }
      dim.width += gap.get() * (labels.size - 1)
      JBInsets.addTo(dim, c.insets)
      return dim
    }

    private fun getLabelSize(label: GHLabel, fm: FontMetrics) =
      Dimension(fm.stringWidth(getLabelText(label)), fm.height)

    private fun getLabelText(label: GHLabel) = " ${label.name} "

    override fun getMaximumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }
  }
}
