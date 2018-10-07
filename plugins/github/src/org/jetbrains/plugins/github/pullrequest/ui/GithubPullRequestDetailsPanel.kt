// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.properties.Delegates


internal class GithubPullRequestDetailsPanel(iconProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : Wrapper(), ComponentWithEmptyText, Disposable {

  private val iconsProvider = iconProviderFactory.create(JBValue.UIInteger("Profile.Icon.Size", 20), this)

  private val emptyText = object : StatusText(this) {
    override fun isStatusVisible() = details == null
  }

  var details: GithubPullRequestDetailedWithHtml?
    by Delegates.observable<GithubPullRequestDetailedWithHtml?>(null) { _, _, _ ->
      update()
    }

  private val contentPanel: JPanel

  private val metaPanel = MetadataPanel().apply {
    border = JBUI.Borders.empty(4, 8, 4, 8)
  }
  private val bodyPanel = BodyPanel().apply {
    border = JBUI.Borders.empty(4, 8, 8, 8)
  }

  init {
    contentPanel = ScrollablePanel(BorderLayout(0, UIUtil.DEFAULT_VGAP)).apply {
      isOpaque = false
      add(metaPanel, BorderLayout.NORTH)
      add(bodyPanel, BorderLayout.CENTER)
    }
    val scrollPane = ScrollPaneFactory.createScrollPane(contentPanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }
    setContent(scrollPane)

    update()

    Disposer.register(this, iconsProvider)
  }

  private fun update() {
    bodyPanel.update()
    metaPanel.update()

    contentPanel.validate()
    contentPanel.isVisible = details != null
  }

  override fun getEmptyText() = emptyText

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    emptyText.paint(this, g)
  }

  private inner class BodyPanel : HtmlPanel() {
    init {
      editorKit = UIUtil.JBWordWrapHtmlEditorKit()
    }

    override fun update() {
      super.update()
      isVisible = !details?.bodyHtml.isNullOrEmpty()
    }

    override fun getBody() = details?.bodyHtml.orEmpty()
    override fun getBodyFont(): Font = UIUtil.getLabelFont()
  }

  private inner class MetadataPanel : JPanel() {
    private val directionPanel = DirectionPanel()

    private val stateLabel = JLabel().apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0, UIUtil.DEFAULT_VGAP * 2, 0)
    }

    private val reviewersLabel = createLabel()
    private val reviewersPanel = createPanel()

    private val assigneesLabel = createLabel()
    private val assigneesPanel = createPanel()

    private val labelsLabel = createLabel()
    private val labelsPanel = createPanel()

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
      add(stateLabel, CC()
        .minWidth("0")
        .spanX(2)
        .wrap())
      addSection(reviewersLabel, reviewersPanel)
      addSection(assigneesLabel, assigneesPanel)
      addSection(labelsLabel, labelsPanel)
    }

    private fun addSection(label: JLabel, panel: JPanel) {
      add(label, CC().alignY("top"))
      add(panel, CC().minWidth("0").growX().pushX().wrap())
    }

    fun update() {
      directionPanel.update()

      val reviewers = details?.requestedReviewers
      reviewersPanel.removeAll()
      if (reviewers == null || reviewers.isEmpty()) {
        reviewersLabel.text = "No Reviewers"
      }
      else {
        reviewersLabel.text = "Reviewers:"
        for (reviewer in reviewers) {
          reviewersPanel.add(createUserLabel(reviewer))
        }
      }

      val assignees = details?.assignees
      assigneesPanel.removeAll()
      if (assignees == null || assignees.isEmpty()) {
        assigneesLabel.text = "Unassigned"
      }
      else {
        assigneesLabel.text = "Assignees:"
        for (assignee in assignees) {
          assigneesPanel.add(createUserLabel(assignee))
        }
      }

      val labels = details?.labels
      labelsPanel.removeAll()
      if (labels == null || labels.isEmpty()) {
        labelsLabel.text = "No Labels"
      }
      else {
        labelsLabel.text = "Labels:"
        for (label in labels) {
          labelsPanel.add(createLabelLabel(label))
        }
      }

      stateLabel.text = ""
      stateLabel.icon = null
      val details = details
      if (details != null && details.state == GithubIssueState.closed)
        if (details.merged) {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is merged"
        }
        else {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is closed"
        }
    }

    private fun createLabel() = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 2, 0, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
    }

    private fun createPanel() = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

    private fun createUserLabel(user: GithubUser) = JLabel(user.login, iconsProvider.getIcon(user), SwingConstants.LEFT).apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
    }

    private fun createLabelLabel(label: GithubIssueLabel) = Wrapper(GithubUIUtil.createIssueLabelLabel(label)).apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 1, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
    }
  }

  private inner class DirectionPanel : NonOpaquePanel(WrapLayout(FlowLayout.LEFT, 0, UIUtil.DEFAULT_VGAP)) {
    private val from = createLabel()
    private val to = createLabel()

    init {
      add(from)
      add(JLabel(" ${UIUtil.rightArrow()} ").apply {
        foreground = CurrentBranchComponent.TEXT_COLOR
        border = JBUI.Borders.empty(0, 5)
      })
      add(to)
    }

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

    fun update() {
      from.text = " ${details?.head?.label.orEmpty()} "
      to.text = " ${details?.base?.ref.orEmpty()} "
    }
  }

  override fun dispose() {}
}