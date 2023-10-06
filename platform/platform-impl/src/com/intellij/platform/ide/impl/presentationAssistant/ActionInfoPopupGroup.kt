// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.Animator
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.SwingUtilities

internal class ActionInfoPopupGroup(project: Project, textFragments: List<TextData>, showAnimated: Boolean) : Disposable {
  data class ActionBlock(val popup: JBPopup, val panel: ActionInfoPanel) {
    val isDisposed: Boolean get() = popup.isDisposed
  }

  private val actionBlocks = textFragments.mapIndexed { index, fragment ->
    val panel = ActionInfoPanel(fragment)
    val popup = createPopup(panel, showAnimated)
    ActionBlock(popup, panel)
  }

  private val hideAlarm = Alarm(this)
  private var animator: Animator
  private val configuration = PresentationAssistant.INSTANCE.configuration
  var phase = Phase.FADING_IN
    private set
  val isShown: Boolean get() = phase == Phase.SHOWN

  enum class Phase { FADING_IN, SHOWN, FADING_OUT, HIDDEN }

  init {
    val connect = ApplicationManager.getApplication().getMessageBus().connect(this)
    connect.subscribe<LafManagerListener>(LafManagerListener.TOPIC, LafManagerListener { updatePopupsBounds(project) })

    animator = FadeInOutAnimator(true, showAnimated)
    actionBlocks.mapIndexed { index, block ->
      block.popup.show(computeLocation(project, index))
    }

    if (showAnimated) {
      animator.resume()
    }
    else {
      phase = Phase.SHOWN
    }

    resetHideAlarm()
  }

  private fun createPopup(panel: ActionInfoPanel, hiddenInitially: Boolean): JBPopup {
    val popup = with(JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel)) {
      if (hiddenInitially) setAlpha(1.0.toFloat())
      setFocusable(false)
      setBelongsToGlobalPopupStack(false)
      setCancelKeyEnabled(false)
      setCancelCallback { phase = Phase.HIDDEN; true }
      createPopup()
    }

    popup.content.background = ActionInfoPanel.BACKGROUND

    popup.addListener(object : JBPopupListener {
      override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}
      override fun onClosed(lightweightWindowEvent: LightweightWindowEvent) {
        phase = Phase.HIDDEN
      }
    })

    return popup
  }

  fun updateText(project: Project, textFragments: List<TextData>) {
    if (actionBlocks.any { it.isDisposed }) return

    actionBlocks.mapIndexed { index, block ->
      block.panel.textData = textFragments[index]
    }

    updatePopupsBounds(project)
    resetHideAlarm()
  }

  private fun updatePopupsBounds(project: Project) {
    actionBlocks.mapIndexed { index, actionBlock ->
      actionBlock.popup.content.let {
        it.validate()
        it.repaint()
      }

      val newBounds = Rectangle(computeLocation(project, index).screenPoint, actionBlock.panel.preferredSize)
      actionBlock.popup.setBounds(newBounds)
    }

    showFinalAnimationFrame()
  }

  fun close() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    phase = Phase.HIDDEN
    actionBlocks.forEach {
      if (!it.popup.isDisposed) {
        it.popup.cancel()
      }
    }
    Disposer.dispose(animator)
  }

  fun canBeReused(size: Int): Boolean = size == actionBlocks.size && (phase == Phase.FADING_IN || phase == Phase.SHOWN)

  private fun getPopupWindows(): List<Window> = actionBlocks.mapNotNull { actionBlock ->
    if (actionBlock.isDisposed) return@mapNotNull null
    val window = SwingUtilities.windowForComponent(actionBlock.popup.content)
    if (window != null && window.isShowing) return@mapNotNull window
    return@mapNotNull null
  }

  private fun setAlpha(alpha: Float) {
    getPopupWindows().forEach {
      WindowManager.getInstance().setAlphaModeRatio(it, alpha)
    }
  }

  private fun resetHideAlarm() {
    hideAlarm.cancelAllRequests()
    hideAlarm.addRequest({ fadeOut() }, configuration.popupDuration, ModalityState.any())
  }

  private fun showFinalAnimationFrame() {
    phase = Phase.SHOWN
    setAlpha(0f)
  }
  private fun fadeOut() {
    if (phase != Phase.SHOWN) return
    phase = Phase.FADING_OUT
    Disposer.dispose(animator)
    animator = FadeInOutAnimator(false, true)
    animator.resume()
  }

  private fun computeLocation(project: Project, index: Int?): RelativePoint {
    val preferredSizes = actionBlocks.map { it.panel.preferredSize }
    val gap = JBUIScale.scale(12)
    val popupGroupSize: Dimension = if (actionBlocks.isNotEmpty()) {
      val totalWidth = preferredSizes.map { it.width }.reduce { total, width -> total + width + gap } - gap
      Dimension(totalWidth, preferredSizes.first().height)
    }
    else Dimension()

    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val statusBarHeight = ideFrame.statusBar?.component?.height ?: 0
    val visibleRect = ideFrame.component.visibleRect

    val x = when (configuration.horizontalAlignment) {
      0 -> visibleRect.x + configuration.margin
      1 -> visibleRect.x + (visibleRect.width - popupGroupSize.width) / 2
      else -> visibleRect.x + visibleRect.width - popupGroupSize.width - configuration.margin
    } + (index?.takeIf {
      0 < index && index < actionBlocks.size
    }?.let {
      // Calculate X for particular popup
      (0..<index).map { preferredSizes[it].width }.reduce { total, width ->
        total + width
      } + gap * index
    } ?: 0)

    val y = when (configuration.verticalAlignment) {
      0 -> visibleRect.y + configuration.margin
      else -> visibleRect.y + visibleRect.height - popupGroupSize.height - statusBarHeight - configuration.margin
    }

    return RelativePoint(ideFrame.component, Point(x, y))
  }

  inner class FadeInOutAnimator(private val forward: Boolean, animated: Boolean) : Animator("Action Hint Fade In/Out", 8, if (animated) 100 else 0, false, forward) {
    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      if (forward && phase != Phase.FADING_IN
          || !forward && phase != Phase.FADING_OUT) return
      setAlpha((totalFrames - frame).toFloat() / totalFrames)
    }

    override fun paintCycleEnd() {
      if (forward) {
        showFinalAnimationFrame()
      }
      else {
        close()
      }
    }
  }
}

//private fun JComponent.addComponentsWithGap(components: List<JComponent>, gap: Int) {
//  val builder = RowsGridBuilder(this)
//  val row = builder.row(resizable = true)
//  for (c in components) {
//    row.cell(c)
//  }
//
//  row.columnsGaps((0 until row.columnsCount).map {
//    val rightGap = if (it == (row.columnsCount - 1)) 0 else gap
//    UnscaledGapsX(0, rightGap)
//  })
//}


//private fun List<Pair<String, Font?>>.mergeFragments(): List<Pair<String, Font?>> {
//  val result = ArrayList<Pair<String, Font?>>()
//  for (item in this) {
//    val last = result.lastOrNull()
//    if (last != null && last.second == item.second) {
//      result.removeAt(result.lastIndex)
//      result.add(Pair(last.first + item.first, last.second))
//    }
//    else {
//      result.add(item)
//    }
//  }
//  return result
//}
//
//private fun createLabels(textFragments: List<Pair<String, Font?>>, ideFrame: IdeFrame): List<JLabel> {
//  var fontSize = configuration.fontSize.toFloat()
//  val color = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
//  val labels = textFragments.mergeFragments().map {
//    @Suppress("HardCodedStringLiteral")
//    val label = JLabel("<html>${it.first}</html>", SwingConstants.CENTER)
//    label.foreground = color
//    if (it.second != null) label.font = it.second
//    label
//  }
//
//  fun setFontSize(size: Float) {
//    for (label in labels) {
//      label.font = label.font.deriveFont(size)
//    }
//    val maxAscent = labels.maxOfOrNull { it.getFontMetrics(it.font).maxAscent } ?: 0
//    for (label in labels) {
//      val ascent = label.getFontMetrics(label.font).maxAscent
//      if (ascent < maxAscent) {
//        label.border = BorderFactory.createEmptyBorder(maxAscent - ascent, 0, 0, 0)
//      }
//      else {
//        label.border = null
//      }
//    }
//  }
//  setFontSize(fontSize)
//  val frameWidth = ideFrame.component.width
//  if (frameWidth > 100) {
//    while (labels.sumOf { it.preferredSize.width } > frameWidth - 10 && fontSize > 12) {
//      setFontSize(--fontSize)
//    }
//  }
//  return labels
//}