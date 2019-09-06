// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.getCommitDetailsBackground
import java.awt.*
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import kotlin.math.max
import kotlin.math.min

abstract class CommitDetailsListPanel<Panel : CommitDetailsPanel>(parent: Disposable) : BorderLayoutPanel(), EditorColorsListener {
  companion object {
    private const val MAX_ROWS = 50
    private const val MIN_SIZE = 20
  }

  private val commitDetailsList = mutableListOf<Panel>()

  private val mainContentPanel = MainContentPanel()
  private val loadingPanel = object : JBLoadingPanel(BorderLayout(), parent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
    override fun getBackground(): Color = getCommitDetailsBackground()
  }
  private val statusText: StatusText = object : StatusText(mainContentPanel) {
    override fun isStatusVisible(): Boolean = this.text.isNotEmpty()
  }

  private val scrollPane =
    JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
      setViewportView(mainContentPanel)
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
    }


  init {
    loadingPanel.add(scrollPane)
    addToCenter(loadingPanel)
  }

  fun startLoadingDetails() {
    loadingPanel.startLoading()
  }

  fun stopLoadingDetails() {
    loadingPanel.stopLoading()
  }

  fun setStatusText(text: String) {
    statusText.text = text
  }

  protected fun rebuildPanel(rows: Int): Int {
    val oldRowsCount = commitDetailsList.size
    val newRowsCount = min(rows, MAX_ROWS)

    for (i in oldRowsCount until newRowsCount) {
      val panel = getCommitDetailsPanel()
      if (i != 0) {
        mainContentPanel.add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
      }
      mainContentPanel.add(panel)
      commitDetailsList.add(panel)
    }

    // clear superfluous items
    while (mainContentPanel.componentCount != 0 && mainContentPanel.componentCount > 2 * newRowsCount - 1) {
      mainContentPanel.remove(mainContentPanel.componentCount - 1)
    }
    while (commitDetailsList.size > newRowsCount) {
      commitDetailsList.removeAt(commitDetailsList.size - 1)
    }

    if (rows > MAX_ROWS) {
      mainContentPanel.add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
      val label = JBLabel("(showing $MAX_ROWS of $rows selected commits)").apply {
        font = FontUtil.getCommitMetadataFont()
        border = JBUI.Borders.emptyLeft(CommitDetailsPanel.SIDE_BORDER)
      }
      mainContentPanel.add(label)
    }

    repaint()
    return newRowsCount
  }

  fun forEachPanelIndexed(f: (Int, Panel) -> Unit) {
    commitDetailsList.forEachIndexed(f)
    update()
  }

  fun setCommits(commits: List<VcsCommitMetadata>) {
    rebuildPanel(commits.size)
    if (commits.isEmpty()) {
      setStatusText(StatusText.DEFAULT_EMPTY_TEXT)
      return
    }
    setStatusText("")
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

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    update()
  }

  override fun getBackground(): Color = getCommitDetailsBackground()

  override fun getMinimumSize(): Dimension {
    val minimumSize = super.getMinimumSize()
    return Dimension(max(minimumSize.width, JBUIScale.scale(MIN_SIZE)), max(minimumSize.height, JBUIScale.scale(MIN_SIZE)))
  }

  private inner class MainContentPanel : JPanel() {
    init {
      layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
      isOpaque = false
    }

    // to fight ViewBorder
    override fun getInsets(): Insets = JBUI.emptyInsets()

    override fun getBackground(): Color = getCommitDetailsBackground()

    override fun paintChildren(g: Graphics) {
      if (statusText.text.isNotEmpty()) {
        statusText.paint(this, g)
      }
      else {
        super.paintChildren(g)
      }
    }
  }
}