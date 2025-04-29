// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableCellState
import com.intellij.ui.components.JBTextField
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.paint.GraphCellPainter
import com.intellij.vcs.log.ui.table.GraphCommitCellController
import com.intellij.vcs.log.ui.table.VcsLogCellController
import com.intellij.vcs.log.ui.table.VcsLogCellRenderer
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.Commit
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager.Companion.getInstance
import com.intellij.vcs.log.ui.table.links.VcsLinksRenderer
import com.intellij.vcs.log.visible.filters.VcsLogTextFilterWithMatches
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.AffineTransform
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.border.Border
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class GraphCommitCellRenderer(
  private val logData: VcsLogData,
  painter: GraphCellPainter,
  private val graphTable: VcsLogGraphTable,
) : TypeSafeTableCellRenderer<GraphCommitCell>(), VcsLogCellRenderer {
  private val component: RealCommitRendererComponent
  private val templateComponent: RealCommitRendererComponent
  private val wipComponent = WipCommitRendererComponent(graphTable, painter)

  init {
    val iconCache = LabelIconCache()
    component = RealCommitRendererComponent(logData, painter, graphTable, iconCache)
    templateComponent = RealCommitRendererComponent(logData, painter, graphTable, iconCache)
  }

  override fun getTableCellRendererComponentImpl(
    table: JTable,
    value: GraphCommitCell,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    return when (value) {
      is GraphCommitCell.RealCommit -> {
        component.apply { customize(value, isSelected, hasFocus, row, column) }
      }
      is GraphCommitCell.NewCommit -> {
        wipComponent.apply { customize(value, isSelected, hasFocus, row, column) }
      }
    }
  }

  private fun getTooltip(value: Any, point: Point, row: Int): JComponent? {
    val cell = getValue(value)
    if (cell !is GraphCommitCell.RealCommit) {
      return null
    }
    val refs = cell.refsToThisCommit
    val bookmarks = cell.bookmarksToThisCommit
    if (refs.isEmpty() && bookmarks.isEmpty()) return null

    prepareTemplateComponent(row, cell)
    if (templateComponent.referencePainter.isLeftAligned) {
      val distance: Double = point.getX() - templateComponent.graphWidth
      if (distance > 0 && distance <= templateComponent.referencesWidth) {
        return TooltipReferencesPanel(logData, refs, bookmarks)
      }
    }
    else {
      if (columnWidth - point.getX() <= templateComponent.referencesWidth) {
        return TooltipReferencesPanel(logData, refs, bookmarks)
      }
    }
    return null
  }

  private fun getTooltipXCoordinate(row: Int): Int {
    val cell = getValue(graphTable.model.getValueAt(row, Commit))
    if (cell !is GraphCommitCell.RealCommit) {
      return columnWidth / 2
    }
    if (cell.refsToThisCommit.isEmpty() && cell.bookmarksToThisCommit.isEmpty()) return columnWidth / 2

    prepareTemplateComponent(row, cell)
    val referencesWidth: Int = templateComponent.referencesWidth
    if (templateComponent.referencePainter.isLeftAligned) {
      return templateComponent.graphWidth + referencesWidth / 2
    }
    return columnWidth - referencesWidth / 2
  }

  private fun prepareTemplateComponent(row: Int, cell: GraphCommitCell.RealCommit) {
    templateComponent.customize(cell, graphTable.isRowSelected(row), graphTable.hasFocus(),
                                row, getInstance().getModelIndex(Commit))
  }

  private val columnWidth: Int
    get() = graphTable.commitColumn.width

  val preferredHeight: Int
    get() = component.preferredHeight

  fun setCompactReferencesView(compact: Boolean) {
    component.referencePainter.isCompact = compact
    templateComponent.referencePainter.isCompact = compact
  }

  fun setShowTagsNames(showTagNames: Boolean) {
    component.referencePainter.showTagNames = showTagNames
    templateComponent.referencePainter.showTagNames = showTagNames
  }

  fun setLeftAligned(leftAligned: Boolean) {
    component.referencePainter.isLeftAligned = leftAligned
    templateComponent.referencePainter.isLeftAligned = leftAligned
  }

  override fun getCellController(): VcsLogCellController {
    return object : GraphCommitCellController(logData, graphTable, component.painter) {
      override fun getTooltipXCoordinate(row: Int): Int {
        return this@GraphCommitCellRenderer.getTooltipXCoordinate(row)
      }

      override fun getTooltip(value: Any, point: Point, row: Int): JComponent? {
        return this@GraphCommitCellRenderer.getTooltip(value, point, row)
      }
    }
  }

  private class RealCommitRendererComponent(
    data: VcsLogData,
    val painter: GraphCellPainter,
    private val table: VcsLogGraphTable,
    iconCache: LabelIconCache,
  ) : SimpleColoredRenderer() {
    private val issueLinkRenderer: IssueLinkRenderer = IssueLinkRenderer(data.project, this)
    private val vcsLinksRenderer: VcsLinksRenderer = VcsLinksRenderer(data.project, this)
    val referencePainter: VcsLogLabelPainter = VcsLogLabelPainter(data, table, iconCache)

    private var printElements: Collection<PrintElement> = emptyList()
    private var fontInner: Font
    private var heightInner: Int
    var graphWidth = 0
    private var affineTransform: AffineTransform?

    init {
      cellState = VcsLogTableCellState()

      fontInner = labelFont
      val configuration = table.graphicsConfiguration
      affineTransform = configuration?.defaultTransform
      heightInner = calculateHeight()
    }

    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      val referencesSize = if (referencePainter.isLeftAligned) 0 else referencePainter.getSize().width
      return Dimension(preferredSize.width + referencesSize, preferredHeight)
    }

    public override fun paintComponent(g: Graphics) {
      super.paintComponent(g)

      val g2d = g as Graphics2D
      if (!referencePainter.isLeftAligned) {
        val start = max(graphWidth.toDouble(), (width - referencePainter.getSize().width).toDouble()).toInt()
        referencePainter.paint(g2d, start, 0, height)
      }
      else {
        referencePainter.paint(g2d, graphWidth, 0, height)
      }
      painter.paint(g2d, printElements)
    }

    fun customize(cell: GraphCommitCell.RealCommit, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      clear()
      setPaintFocusBorder(false)
      acquireState(table, isSelected, hasFocus, row, column)
      cellState.updateRenderer(this)

      printElements = cell.printElements
      graphWidth = GraphCommitCellUtil.getGraphWidth(table, printElements)

      val style = table.applyHighlighters(this, row, column, hasFocus, isSelected)

      val refs = cell.refsToThisCommit
      val bookmarks = cell.bookmarksToThisCommit
      val labelForeground = if (isNewUI()) {
        JBColor.namedColor("VersionControl.Log.Commit.Reference.foreground",
                           CurrentBranchComponent.TEXT_COLOR)
      }
      else {
        if (isSelected) table.getBaseStyle(row, column, hasFocus, isSelected).foreground!!
        else CurrentBranchComponent.TEXT_COLOR
      }

      append("") // appendTextPadding wont work without this
      val renderLinks = !cell.isLoading
      if (referencePainter.isLeftAligned) {
        referencePainter.customizePainter(refs, bookmarks, background, labelForeground, isSelected,
                                          getAvailableWidth(column, graphWidth))

        var referencesWidth: Int = referencePainter.getSize().width
        if (referencesWidth > 0) referencesWidth += LabelPainter.RIGHT_PADDING.get()
        appendTextPadding(graphWidth + referencesWidth)
        appendText(cell, style, isSelected, renderLinks)
      }
      else {
        appendTextPadding(graphWidth)
        appendText(cell, style, isSelected, renderLinks)
        referencePainter.customizePainter(refs, bookmarks, background, labelForeground, isSelected,
                                          getAvailableWidth(column, graphWidth))
      }
    }

    private fun appendText(cell: GraphCommitCell.RealCommit, style: SimpleTextAttributes, isSelected: Boolean, renderLinks: Boolean) {
      val cellText = StringUtil.replace(cell.text, "\t", " ").trim { it <= ' ' }
      val commitId = cell.commitId

      if (renderLinks) {
        if (VcsLinksRenderer.isEnabled()) {
          vcsLinksRenderer.appendTextWithLinks(cellText, style, commitId)
        }
        else {
          issueLinkRenderer.appendTextWithLinks(cellText, style)
        }
      }
      else {
        append(cellText, style)
      }

      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, isSelected)
      if (`is`("vcs.log.filter.text.highlight.matches")) {
        val textFilter = table.model.visiblePack.filters.get(VcsLogFilterCollection.TEXT_FILTER)
        if (textFilter is VcsLogTextFilterWithMatches) {
          val text = getCharSequence(false).toString()
          SpeedSearchUtil.applySpeedSearchHighlighting(this, textFilter.matchingRanges(text), isSelected)
        }
      }
    }

    private fun getAvailableWidth(column: Int, graphWidth: Int): Int {
      val textAndLabelsWidth = table.columnModel.getColumn(column).width - graphWidth
      val freeSpace = textAndLabelsWidth - super.getPreferredSize().width
      val allowedSpace = if (referencePainter.isCompact) {
        min(freeSpace.toDouble(), (textAndLabelsWidth / 3).toDouble()).toInt()
      }
      else {
        max(freeSpace.toDouble(), max((textAndLabelsWidth / 2).toDouble(),
                                      (textAndLabelsWidth - scale(
                                        DISPLAYED_MESSAGE_PART)).toDouble())).toInt()
      }
      return max(0.0, allowedSpace.toDouble()).toInt()
    }

    private fun calculateHeight(): Int {
      val rowContentHeight = calculateRowContentHeight()
      return if (isNewUI()) max(rowContentHeight.toDouble(), JBUI.CurrentTheme.VersionControl.Log.rowHeight().toDouble()).toInt()
      else rowContentHeight
    }

    private fun calculateRowContentHeight(): Int {
      return max(referencePainter.getSize().height.toDouble(),
                 (getFontMetrics(fontInner).height + JBUI.scale(
                   JBUI.CurrentTheme.VersionControl.Log.verticalPadding())).toDouble()).toInt()
    }

    val preferredHeight: Int
      get() {
        val font = labelFont
        val configuration = table.graphicsConfiguration
        if (fontInner !== font || (configuration != null && affineTransform != configuration.defaultTransform)) {
          fontInner = font
          affineTransform = configuration?.defaultTransform
          heightInner = calculateHeight()
        }
        return heightInner
      }

    override fun getFontMetrics(font: Font): FontMetrics {
      return table.getFontMetrics(font)
    }

    val referencesWidth: Int
      get() = referencePainter.getSize().width

    companion object {
      private const val DISPLAYED_MESSAGE_PART = 80
    }
  }

  private class WipCommitRendererComponent(
    private val graphTable: VcsLogGraphTable,
    private val painter: GraphCellPainter,
  ) : JComponent() {
    private var printElements: Collection<PrintElement> = emptyList()

    private val commitTextField = JBTextField("").apply {
      emptyText.text = VcsLogBundle.message("vcs.log.wip.label")
      foreground = UIUtil.getContextHelpForeground()
      background = JBColor.lazy { UIUtil.getTextFieldBackground() }
    }

    init {
      add(commitTextField)
    }

    fun customize(value: GraphCommitCell.NewCommit, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      commitTextField.text = value.text.takeIf { it.isNotEmpty() } ?: VcsLogBundle.message("vcs.log.wip.label")
      printElements = value.printElements
      graphTable.applyHighlighters(this, row, column, hasFocus, isSelected)
    }

    override fun paintComponent(g: Graphics) {
      val g2d = g as Graphics2D
      painter.paint(g2d, printElements)
    }

    override fun doLayout() {
      positionTextField()
    }

    private fun positionTextField() {
      val graphWidth = GraphCommitCellUtil.getGraphWidth(graphTable, printElements)
      val leftHorizontalBorder = maxOf(DEFAULT_HORIZONTAL_BORDER, graphWidth)
      commitTextField.setBounds(leftHorizontalBorder, VERTICAL_BORDER, bounds.width - leftHorizontalBorder - DEFAULT_HORIZONTAL_BORDER, bounds.height - 2 * VERTICAL_BORDER)
    }

    companion object {
      const val DEFAULT_HORIZONTAL_BORDER = 4
      const val VERTICAL_BORDER = 2
    }
  }

  class VcsLogTableCellState : TableCellState() {
    override fun getBorder(isSelected: Boolean, hasFocus: Boolean): Border? {
      return null
    }

    override fun getSelectionForeground(table: JTable, isSelected: Boolean): Color {
      if (!isSelected) return super.getSelectionForeground(table, isSelected)
      return VcsLogGraphTable.getSelectionForeground(RenderingUtil.isFocused(table))
    }
  }

  companion object {
    @JvmStatic
    val labelFont: Font
      get() = StartupUiUtil.labelFont
  }
}
