package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.goPerformanceToolWindow(action: GoPerformanceToolWindowUI.() -> Unit = {}): GoPerformanceToolWindowUI =
  x(GoPerformanceToolWindowUI::class.java) {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAccessibleName("Go Optimization")
    )
  }.apply(action)

fun IdeaFrameUI.flameGraphViewSettingsPopup(anchor: String): UiComponent =
  x {
    componentWithChild(
      byClass("HeavyWeightWindow"),
      byClass("MyList") and contains(byVisibleText(anchor))
    )
  }

class GoPerformanceToolWindowUI(data: ComponentData) : ToolWindowUiComponent(data) {
  fun viewerTab(name: String): UiComponent =
    x { and(byClass("SimpleColoredComponent"), byAccessibleName(name)) }

  val graphViewport: UiComponent = x { byType("com.intellij.uml.components.UmlGraphZoomableViewport") }

  val viewSettingsButton: UiComponent = x { and(byClass("ActionButton"), byAccessibleName("View Settings")) }

  val sampleTypeSelectorLabel: UiComponent = x { byAccessibleName("Show:") }

  val runWithProfilerButton: UiComponent = x { byAccessibleName("Run with Profiler") }

  val chooseConfigurationLabel: UiComponent = x { contains(byVisibleText("Choose configuration")) }

  fun profilerToolbarButton(text: String): UiComponent = x { byAccessibleName(text) }

  val startCpuRecordingButton: UiComponent get() = profilerToolbarButton("Start CPU Recording")

  val stopCpuRecordingButton: UiComponent get() = profilerToolbarButton("Stop CPU Recording")

  val moreProfilesButton: UiComponent = x { and(byClass("JButton"), byAccessibleName("More Profiles")) }
}
