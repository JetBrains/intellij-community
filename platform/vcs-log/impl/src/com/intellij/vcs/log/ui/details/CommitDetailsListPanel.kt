// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.getCommitDetailsBackground
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.ScrollPaneConstants
import kotlin.math.max
import kotlin.math.min

abstract class CommitDetailsListPanel<Panel : CommitDetailsPanel>(parent: Disposable) :
  BorderLayoutPanel(),
  EditorColorsListener,
  ComponentWithEmptyText {

  companion object {
    private const val MAX_ROWS = 50
    private const val MIN_SIZE = 20
  }

  private val commitDetailsList = mutableListOf<Panel>()

  private val viewPanel = ViewPanel()
  private val loadingPanel = object : JBLoadingPanel(BorderLayout(), parent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
    override fun getBackground(): Color = getCommitDetailsBackground()
  }.apply {
    val scrollPane = JBScrollPane(
      viewPanel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )
    scrollPane.border = JBUI.Borders.empty()
    scrollPane.viewportBorder = JBUI.Borders.empty()
    add(scrollPane, BorderLayout.CENTER)
  }

  private val mainContentPanel = object : BorderLayoutPanel() {
    init {
      isOpaque = false
      addToCenter(loadingPanel)
    }

    val statusText: StatusText = object : StatusText(this) {
      override fun isStatusVisible(): Boolean = isEmptyStatusVisible()
    }

    override fun paintChildren(g: Graphics) {
      if (isEmptyStatusVisible()) {
        statusText.paint(this, g)
      }
      else {
        super.paintChildren(g)
      }
    }
  }


  init {
    addToCenter(mainContentPanel)
  }

  fun startLoadingDetails() {
    loadingPanel.startLoading()
  }

  fun stopLoadingDetails() {
    loadingPanel.stopLoading()
  }

  fun setStatusText(@Nls(capitalization = Nls.Capitalization.Sentence) text: String) {
    mainContentPanel.statusText.text = text
  }

  protected fun rebuildPanel(rows: Int): Int {
    val oldRowsCount = commitDetailsList.size
    val newRowsCount = min(rows, MAX_ROWS)

    for (i in oldRowsCount until newRowsCount) {
      val panel = getCommitDetailsPanel()
      if (i != 0) {
        viewPanel.add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
      }
      viewPanel.add(panel)
      commitDetailsList.add(panel)
    }

    // clear superfluous items
    while (viewPanel.componentCount != 0 && viewPanel.componentCount > 2 * newRowsCount - 1) {
      viewPanel.remove(viewPanel.componentCount - 1)
    }
    while (commitDetailsList.size > newRowsCount) {
      commitDetailsList.removeAt(commitDetailsList.size - 1)
    }

    if (rows > MAX_ROWS) {
      viewPanel.add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
      val label = JBLabel(VcsLogBundle.message("vcs.log.details.showing.selected.commits", MAX_ROWS, rows)).apply {
        font = FontUtil.getCommitMetadataFont()
        border = JBUI.Borders.emptyLeft(CommitDetailsPanel.SIDE_BORDER)
      }
      viewPanel.add(label)
    }

    revalidate()
    repaint()
    return newRowsCount
  }

  fun forEachPanelIndexed(f: (Int, Panel) -> Unit) {
    commitDetailsList.forEachIndexed(f)
    update()
  }

  fun setCommits(commits: List<VcsCommitMetadata>) {
    rebuildPanel(commits.size)
    forEachPanelIndexed { i, panel ->
      val commit = commits[i]
      panel.setCommit(commit)
    }
  }

  fun update() {
    commitDetailsList.forEach { it.update() }
  }

  protected open fun navigate(commitId: CommitId) {}

  protected abstract fun getCommitDetailsPanel(): Panel

  protected open fun isEmptyStatusVisible(): Boolean = commitDetailsList.isEmpty()

  override fun getEmptyText(): StatusText = mainContentPanel.statusText

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    update()
  }

  override fun getBackground(): Color = getCommitDetailsBackground()

  override fun getMinimumSize(): Dimension {
    val minimumSize = super.getMinimumSize()
    return Dimension(max(minimumSize.width, JBUIScale.scale(MIN_SIZE)), max(minimumSize.height, JBUIScale.scale(MIN_SIZE)))
  }

  private inner class ViewPanel : ScrollablePanel(
    VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
  ) {
    init {
      isOpaque = false
      border = JBUI.Borders.empty()
    }

    override fun getBackground(): Color = getCommitDetailsBackground()
  }
}