// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.Disposable
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
    val popup = createPopup(panel, true)
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
    animator = FadeInOutAnimator(true, showAnimated)
    actionBlocks.mapIndexed { index, block ->
      block.popup.show(computeLocation(project, index))
    }
    animator.resume()
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

    showFinal()
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

    if (index != null) println("ayay calculated for $index: ${Rectangle(Point(x, y), preferredSizes[index])}")

    return RelativePoint(ideFrame.component, Point(x, y))
  }

  private fun fadeOut() {
    if (phase != Phase.SHOWN) return
    phase = Phase.FADING_OUT
    Disposer.dispose(animator)
    animator = FadeInOutAnimator(false, true)
    animator.resume()
  }

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

  private fun showFinal() {
    phase = Phase.SHOWN
    setAlpha(0f)
    hideAlarm.cancelAllRequests()
    hideAlarm.addRequest({ fadeOut() }, configuration.popupDuration, ModalityState.any())
  }

  inner class FadeInOutAnimator(private val forward: Boolean, animated: Boolean) : Animator("Action Hint Fade In/Out", 8, if (animated) 100 else 0, false, forward) {
    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      if (forward && phase != Phase.FADING_IN
          || !forward && phase != Phase.FADING_OUT) return
      setAlpha((totalFrames - frame).toFloat() / totalFrames)
    }

    override fun paintCycleEnd() {
      if (forward) {
        showFinal()
      }
      else {
        close()
      }
    }
  }
}