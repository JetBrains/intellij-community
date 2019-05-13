// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubPullRequest
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.ui.WrapLayout
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class GithubPullRequestMetadataPanel(private val iconsProvider: CachingGithubAvatarIconsProvider) : JPanel() {
  private val directionPanel = DirectionPanel()
  var direction: Pair<GithubPullRequest.Tag, GithubPullRequest.Tag>?
    get() = directionPanel.direction
    set(value) {
      directionPanel.direction = value
    }

  private val reviewersHandle = LabeledListPanelHandle.create("No Reviewers", "Reviewers:", ::createUserLabel)
  var reviewers: List<GithubUser>?
    get() = reviewersHandle.list
    set(value) {
      reviewersHandle.list = value
    }

  private val assigneesHandle = LabeledListPanelHandle.create("Unassigned", "Assignees:", ::createUserLabel)
  var assignees: List<GithubUser>?
    get() = assigneesHandle.list
    set(value) {
      assigneesHandle.list = value
    }

  private val labelsHandle = LabeledListPanelHandle.create("No Labels", "Labels:", ::createLabelLabel)
  var labels: List<GithubIssueLabel>?
    get() = labelsHandle.list
    set(value) {
      labelsHandle.list = value
    }

  init {
    isOpaque = false
    layout = MigLayout(LC()
                         .fillX()
                         .gridGap("0", "0")
                         .insets("0", "0", "0", "0"))

    add(directionPanel, CC()
      .minWidth("0")
      .spanX(2).growX()
      .wrap())
    addListPanel(reviewersHandle)
    addListPanel(assigneesHandle)
    addListPanel(labelsHandle)
  }

  private fun addListPanel(handle: LabeledListPanelHandle<*>) {
    add(handle.label, CC().alignY("top"))
    add(handle.panel, CC().minWidth("0").growX().pushX().wrap())
  }

  private fun createUserLabel(user: GithubUser) = JLabel(user.login, iconsProvider.getIcon(user), SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
  }

  private fun createLabelLabel(label: GithubIssueLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }

  private abstract class LabeledListPanelHandle<T>(emptyText: String, notEmptyText: String) {
    val label = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 2, 0, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
    }
    val panel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

    var list: List<T>? by equalVetoingObservable<List<T>?>(null) {
      label.text = it?.let { text -> if (text.isEmpty()) emptyText else notEmptyText }
      label.isVisible = it != null

      panel.removeAll()
      panel.isVisible = it != null
      if (it != null) for (item in it) {
        panel.add(getListItemComponent(item))
      }
    }

    abstract fun getListItemComponent(item: T): JComponent

    companion object {
      inline fun <T> create(emptyText: String, notEmptyText: String, crossinline componentProvider: (T) -> JComponent) =
        object : LabeledListPanelHandle<T>(emptyText, notEmptyText) {
          override fun getListItemComponent(item: T) = componentProvider(item)
        }
    }
  }

  private class DirectionPanel : NonOpaquePanel(WrapLayout(FlowLayout.LEFT, 0, UIUtil.DEFAULT_VGAP)) {
    private val from = createLabel()
    private val to = createLabel()

    var direction: Pair<GithubPullRequest.Tag, GithubPullRequest.Tag>?
      by equalVetoingObservable<Pair<GithubPullRequest.Tag, GithubPullRequest.Tag>?>(null) {
        from.text = " ${it?.first?.label} "
        to.text = " ${it?.second?.ref} "
        this@DirectionPanel.isVisible = it != null
      }

    init {
      add(from)
      add(JLabel(" ${UIUtil.rightArrow()} ").apply {
        foreground = CurrentBranchComponent.TEXT_COLOR
        border = JBUI.Borders.empty(0, 5)
      })
      add(to)
    }

    companion object {
      private fun createLabel() = object : JBLabel(UIUtil.ComponentStyle.REGULAR) {
        init {
          updateColors()
        }

        override fun updateUI() {
          super.updateUI()
          updateColors()
        }

        private fun updateColors() {
          foreground = CurrentBranchComponent.TEXT_COLOR
          background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
        }
      }.andOpaque()
    }
  }
}