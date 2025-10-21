// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile.ProfileListener
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.getCommitDetailsBackground
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import kotlin.math.max

class CommitDetailsListPanel
@JvmOverloads constructor(private val project: Project, parent: Disposable,
                          private val createDetailsPanel: () -> CommitDetailsPanel = { CommitDetailsPanel() }) :
  BorderLayoutPanel(),
  EditorColorsListener,
  ComponentWithEmptyText {

  companion object {
    private const val MIN_SIZE = 20
  }

  private var displayedCommits: List<CommitId> = emptyList()

  private val statusText: StatusText = object : StatusText(this) {
    override fun isStatusVisible(): Boolean = displayedCommits.isEmpty()
  }

  private val detailsPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)).apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }
  private val viewPanel = ScrollablePanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty()
    add(detailsPanel, BorderLayout.CENTER)
  }
  private val loadingPanel = JBLoadingPanel(BorderLayout(), parent, ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS).apply {
    isOpaque = false

    val scrollPane = JBScrollPane(
      viewPanel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
      isOpaque = false
      viewport.isOpaque = false
    }
    add(scrollPane, BorderLayout.CENTER)
  }

  init {
    project.messageBus.connect(parent).subscribe(CommitMessageInspectionProfile.TOPIC, ProfileListener { update() })

    setStatusText(VcsLogBundle.message("vcs.log.commit.details.status"))

    background = getCommitDetailsBackground()

    addToCenter(loadingPanel)

    putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, statusText.wrappedFragmentsIterable)
  }

  fun startLoadingDetails() {
    loadingPanel.startLoading()
  }

  fun stopLoadingDetails() {
    loadingPanel.stopLoading()
  }

  fun setStatusText(@Nls(capitalization = Nls.Capitalization.Sentence) text: String) {
    statusText.text = text
  }

  internal fun rebuildPanel(commits: List<CommitId>) {
    val oldRowsCount = displayedCommits.size
    displayedCommits = commits
    val newRowsCount = displayedCommits.size

    for (i in oldRowsCount until newRowsCount) {
      val panel = createDetailsPanel()
      if (i != 0) {
        detailsPanel.add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
      }
      detailsPanel.add(panel)
    }

    // clear superfluous items
    while (detailsPanel.componentCount != 0 && detailsPanel.componentCount > 2 * newRowsCount - 1) {
      detailsPanel.remove(detailsPanel.componentCount - 1)
    }

    revalidate()
    repaint()
  }

  fun showOverflowLabelIfNeeded(max: Int, requested: Int) {
    val componentCount = viewPanel.componentCount
    if (componentCount > 1) {
      viewPanel.remove(componentCount - 1)
    }

    if (requested > max) {
      val overflowLabelPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)).apply {
        isOpaque = false

        add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
        add(JBLabel(VcsLogBundle.message("vcs.log.details.showing.selected.commits", max, requested)).apply {
          font = FontUtil.getCommitMetadataFont()
          border = JBUI.Borders.emptyLeft(CommitDetailsPanel.SIDE_BORDER)
        })
      }
      viewPanel.add(overflowLabelPanel, BorderLayout.SOUTH)
    }

    viewPanel.revalidate()
    viewPanel.repaint()
  }

  override fun paintChildren(g: Graphics) {
    statusText.paint(this, g)
    super.paintChildren(g)
  }

  fun forEachPanelIndexed(f: (Int, CommitDetailsPanel) -> Unit) {
    var idx = 0
    detailsPanel.components.forEach {
      if (it is CommitDetailsPanel) {
        f(idx, it)
        idx++
      }
    }
    update()
  }

  fun forEachPanel(consumer: (CommitId, CommitDetailsPanel) -> Unit) {
    forEachPanelIndexed { idx, panel ->
      consumer(displayedCommits[idx], panel)
    }
  }

  fun setCommits(commits: List<VcsCommitMetadata>) {
    rebuildPanel(commits.map { CommitId(it.id, it.root) })
    forEachPanelIndexed { i, panel ->
      val commit = commits[i]
      val presentation = CommitPresentationUtil.buildPresentation(project, commit, mutableSetOf())
      panel.setCommit(presentation)
    }
  }

  fun update() {
    detailsPanel.components.forEach {
      if (it is CommitDetailsPanel) it.update()
    }
  }

  override fun getEmptyText(): StatusText = statusText

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    update()
  }

  override fun getMinimumSize(): Dimension {
    val minimumSize = super.getMinimumSize()
    return Dimension(max(minimumSize.width, JBUIScale.scale(MIN_SIZE)), max(minimumSize.height, JBUIScale.scale(MIN_SIZE)))
  }
}