package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent

/** The Go Optimization tool window, seen from the Escape Analysis tool. */
class GoEscapeAnalysisUi(data: ComponentData) : UiComponent(data) {
  /** The list of the Go Optimization tools. A selected item shows the panel of its tool. */
  val toolSelector: JListUiComponent = x(JListUiComponent::class.java) { byClass("JBList") }

  /** The header of the Escape Analysis input panel. */
  val analyzeForLabel: UiComponent = x { byVisibleText("Analyze for:") }

  val runButton: UiComponent = x { byVisibleText("Run Escape Analysis") }

  val resultsTree: JTreeUiComponent = x(JTreeUiComponent::class.java) { byAccessibleName("GoEscapeAnalysisResultsTree") }

  /** The splitter that holds the results tree and the detail pane. */
  private val treeSplitter: UiComponent = x { byAccessibleName("GoEscapeAnalysisTreeSplitter") }

  /**
   * The pane on the right of the results tree. It shows the detail of the selected node.
   *
   * The product gives the pane an accessible name, but the name does not reach the UI hierarchy, so the search
   * goes by class. The splitter keeps the search away from another editor pane of the IDE.
   */
  val detailPane: UiComponent = treeSplitter.x { byClass("JEditorPane") }

  /** The toolbar on the left of the results tree. It holds the rerun, collapse and expand actions. */
  val sideToolbar: UiComponent = x { byAccessibleName("GoEscapeAnalysisSideToolbar") }

  /** The tab with the tool input panel. The tool window shows it only next to a result tab. */
  val toolsTab: UiComponent = x { and(byClass("ContentTabLabel"), contains(byVisibleText("Tools"))) }

  /** The tabs with the results of the finished runs. */
  val resultTabs: UIComponentsList<UiComponent> =
    xx { and(byClass("ContentTabLabel"), contains(byVisibleText("Escape analysis"))) }

  /**
   * The action button of the side toolbar with [tooltip].
   *
   * The search stays inside the toolbar. The Project View has its own buttons with the same tooltips.
   */
  fun sideToolbarButton(tooltip: String): UiComponent = sideToolbar.x { byTooltip(tooltip) }
}

internal fun IdeaFrameUI.goEscapeAnalysisUi(): GoEscapeAnalysisUi =
  x(GoEscapeAnalysisUi::class.java) {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAccessibleName("Go Optimization"),
    )
  }
