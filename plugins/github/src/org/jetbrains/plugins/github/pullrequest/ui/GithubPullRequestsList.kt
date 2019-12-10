// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

internal class GithubPullRequestsList(private val copyPasteManager: CopyPasteManager,
                                      avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                      model: ListModel<GHPullRequestShort>)
  : JBList<GHPullRequestShort>(model), CopyProvider, DataProvider, Disposable {

  private val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, this)

  init {
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    addMouseListener(RightClickSelectionListener())

    val renderer = PullRequestsListCellRenderer()
    cellRenderer = renderer
    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

    ScrollingUtil.installActions(this)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val childComponent = ListUtil.getDeepestRendererChildComponentAt(this, event.point)
    if (childComponent !is JComponent) return null
    return childComponent.toolTipText
  }

  override fun performCopy(dataContext: DataContext) {
    if (selectedIndex < 0) return
    val selection = model.getElementAt(selectedIndex)
    copyPasteManager.setContents(StringSelection("#${selection.number} ${selection.title}"))
  }

  override fun isCopyEnabled(dataContext: DataContext) = !isSelectionEmpty

  override fun isCopyVisible(dataContext: DataContext) = false

  override fun getData(dataId: String): Any? = when {
    PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
    GithubPullRequestKeys.SELECTED_PULL_REQUEST.`is`(dataId) -> selectedValue
    else -> null
  }

  override fun dispose() {}

  private inner class PullRequestsListCellRenderer : ListCellRenderer<GHPullRequestShort>, JPanel() {

    private val stateIcon = JLabel()
    private val title = JLabel()
    private val info = JLabel()
    private val labels = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
    }
    private val assignees = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
    }

    init {
      border = JBUI.Borders.empty(5, 8)

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())

      val gapAfter = "${JBUI.scale(5)}px"
      add(stateIcon, CC()
        .gapAfter(gapAfter))
      add(title, CC()
        .minWidth("pref/2px")
        .gapAfter(gapAfter))
      add(labels, CC()
        .growX()
        .pushX()
        .minWidth("0px")
        .gapAfter(gapAfter))
      add(assignees, CC()
        .spanY(2)
        .wrap())
      add(info, CC()
        .minWidth("0px")
        .skip(1)
        .spanX(2))
    }

    override fun getListCellRendererComponent(list: JList<out GHPullRequestShort>,
                                              value: GHPullRequestShort,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
      val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
      val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

      stateIcon.apply {
        icon = when (value.state) {
          GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
          GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
          GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
        }
      }
      title.apply {
        text = value.title
        foreground = primaryTextColor
      }
      info.apply {
        text = "#${value.number} ${value.author?.login} on ${DateFormatUtil.formatDate(value.createdAt)}"
        foreground = secondaryTextColor
      }
      labels.apply {
        removeAll()
        for (label in value.labels) {
          add(GithubUIUtil.createIssueLabelLabel(label))
          add(Box.createRigidArea(JBDimension(4, 0)))
        }
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

      return this
    }
  }

  private inner class RightClickSelectionListener : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      if (SwingUtilities.isRightMouseButton(e)) {
        val row = locationToIndex(e.point)
        if (row != -1) selectionModel.setSelectionInterval(row, row)
      }
    }
  }
}