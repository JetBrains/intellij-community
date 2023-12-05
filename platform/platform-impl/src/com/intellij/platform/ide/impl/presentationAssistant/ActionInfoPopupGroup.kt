// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.width
import com.intellij.util.Alarm
import com.intellij.util.ui.Animator
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

internal class ActionInfoPopupGroup(val project: Project, textFragments: List<TextData>, showAnimated: Boolean) : Disposable {
  data class ActionBlock(val popup: JBPopup, val panel: ActionInfoPanel) {
    val isDisposed: Boolean get() = popup.isDisposed
  }

  private val configuration = service<PresentationAssistant>().configuration
  private val appearance = appearanceFromSize(PresentationAssistantPopupSize.from(configuration.popupSize),
                                              PresentationAssistantTheme.fromValueOrDefault(configuration.theme))
  private val actionBlocks = textFragments.map { fragment ->
    val panel = ActionInfoPanel(fragment, appearance)
    val popup = createPopup(panel, showAnimated)
    ActionBlock(popup, panel)
  }
  private val settingsButton = PresentationAssistantQuickSettingsButton(project, appearance) { isSettingsButtonForcedToBeShown = (it > 0) }

  private val settingsButtonLocation: RelativePoint get() {
    return actionBlocks.lastOrNull()?.popup?.let { popup ->
      val location = popup.locationOnScreen
      location.x += popup.width + appearance.spaceBetweenPopups
      RelativePoint(location)
    } ?: computeLocation(project, actionBlocks.size).popupLocation
  }

  private var isPopupHovered: Boolean = false
    set(value) {
      val oldValue = field
      field = value

      if (oldValue != isPopupHovered) {
        if (isPopupHovered) {
          settingsButton.acquireShownStateRequest(settingsButtonLocation)
        }
        else {
          settingsButton.releaseShownStateRequest()
        }
      }
      updateForcedToBeShown()
    }

  private var isSettingsButtonForcedToBeShown: Boolean = true
    set(value) {
      field = value
      updateForcedToBeShown()
    }

  private var forcedToBeShown: Boolean = false
    set(value) {
      if (value != field) {
        if (value) hideAlarm.cancelAllRequests()
        else if (isShown) resetHideAlarm()
      }
      field = value
    }

  private val hideAlarm = Alarm(this)
  private var animator: Animator
  var phase = Phase.FADING_IN
    private set
  val isShown: Boolean get() = phase == Phase.SHOWN

  enum class Phase { FADING_IN, SHOWN, FADING_OUT, HIDDEN }

  init {
    val connect = ApplicationManager.getApplication().getMessageBus().connect(this)
    connect.subscribe<LafManagerListener>(LafManagerListener.TOPIC, LafManagerListener { updatePopupsBounds(project) })

    addWindowListener(project, object : WindowAdapter() {
      override fun windowDeactivated(ev: WindowEvent) {
        // Hide popup on switching to another non-IDE window. Otherwise, the popup stays on top of another application
        // The issue reproduces only on macOS
        if (ev.oppositeWindow == null && SystemInfo.isMac) close()
      }
    })

    animator = FadeInOutAnimator(true, showAnimated)
    actionBlocks.mapIndexed { index, block ->
      block.popup.show(computeLocation(project, index).popupLocation)
    }

    if (showAnimated) {
      animator.resume()
    }
    else {
      phase = Phase.SHOWN
    }

    resetHideAlarm()
  }

  private fun addWindowListener(project: Project, listener: WindowAdapter) {
    val frame = WindowManager.getInstance().getFrame(project)!!
    frame.addWindowListener(listener)
    Disposer.register(this) {
      frame.removeWindowListener(listener)
    }
  }

  private fun createPopup(panel: ActionInfoPanel, hiddenInitially: Boolean): JBPopup {
    val popup = with(JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel)) {
      if (hiddenInitially) setAlpha(1.0.toFloat())
      setBorderColorIfNeeded(PresentationAssistantTheme.fromValueOrDefault(configuration.theme))
      setFocusable(false)
      setBelongsToGlobalPopupStack(false)
      setCancelKeyEnabled(false)
      setCancelCallback { phase = Phase.HIDDEN; true }
      createPopup()
    }

    popup.content.background = PresentationAssistantTheme.fromValueOrDefault(configuration.theme).background

    popup.addListener(object : JBPopupListener {
      override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}
      override fun onClosed(lightweightWindowEvent: LightweightWindowEvent) {
        phase = Phase.HIDDEN
      }
    })

    popup.content.addMouseHoverListener(this, object: HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) { isPopupHovered = true }
      override fun mouseMoved(component: Component, x: Int, y: Int) { isPopupHovered = true }
      override fun mouseExited(component: Component) { isPopupHovered = false }
    })

    val moveListener = object : WindowMoveListener(panel) {
      private var isDragging = false

      override fun mouseDragged(event: MouseEvent?) {
        super.mouseDragged(event)
        updateDelta(event, false)
        isDragging = true
      }

      override fun mouseReleased(event: MouseEvent?) {
        super.mouseReleased(event)
        if (isDragging) {
          updateDelta(event, true)
        }
        isDragging = false
      }

      override fun mouseExited(e: MouseEvent?) {
        super.mouseExited(e)
        if (isDragging) {
          updateDelta(e, true)
        }
        isDragging = false
      }

      private fun updateDelta(event: MouseEvent?, isFinal: Boolean) {
        val actionInfoPanel = event?.component as? ActionInfoPanel?: return
        saveLocationDelta(project, actionInfoPanel)

        if (isFinal) {
          validateAndFixLocationDelta(project)
          updatePopupsBounds(project, null)
        }
        else {
          updatePopupsBounds(project, actionInfoPanel)
        }
      }
    }.installTo(panel)
    Disposer.register(this) { moveListener.uninstallFrom(panel) }

    return popup
  }

  fun updateText(project: Project, textFragments: List<TextData>) {
    if (actionBlocks.any { it.isDisposed }) return

    actionBlocks.mapIndexed { index, block ->
      block.panel.textData = textFragments[index]
      getPopupWindow(block.popup)?.toFront()
    }

    updatePopupsBounds(project)
    resetHideAlarm()
  }

  private fun updatePopupsBounds(project: Project, ignoredPanel: ActionInfoPanel? = null) {
    actionBlocks.mapIndexed { index, actionBlock ->
      if (actionBlock.panel == ignoredPanel) return@mapIndexed

      actionBlock.popup.content.let {
        it.validate()
        it.repaint()
      }

      val newBounds = Rectangle(computeLocation(project, index).popupLocation.screenPoint, actionBlock.panel.preferredSize)
      actionBlock.popup.setBounds(newBounds)
    }

    settingsButton.hidePopup()
    settingsButton.updatePreferredSize()
    showFinalAnimationFrame()
  }

  private fun saveLocationDelta(project: Project, panel: ActionInfoPanel?) {
    panel ?: return
    val index = actionBlocks.indexOfFirst { it.panel == panel }
    if (index < 0) return

    val window = getPopupWindow(actionBlocks[index].popup) ?: return

    val newAlignment = calculateNewAlignmentAfterDrag(window)
    configuration.horizontalAlignment = newAlignment.x
    configuration.verticalAlignment = newAlignment.y

    val originalLocation = computeLocation(project, index, true).popupLocation.screenPoint
    applyDeltaToConfiguration(originalLocation, window.location)
  }

  private fun validateAndFixLocationDelta(project: Project) {
    val newGroupBounds = computeLocation(project, null).let {
      Rectangle(it.popupLocation.screenPoint, it.groupSize)
    }
    val validatedGroupBounds = newGroupBounds.clone() as Rectangle

    val ideFrameBounds = ideFrameScreenBounds(project)
    ScreenUtil.moveToFit(validatedGroupBounds, ideFrameBounds, JBInsets.emptyInsets())

    if (newGroupBounds != validatedGroupBounds) {
      val originalGroupLocation = computeLocation(project, null, true).popupLocation.screenPoint
      applyDeltaToConfiguration(originalGroupLocation, validatedGroupBounds.location)
    }
  }

  private fun applyDeltaToConfiguration(original: Point, new: Point) {
    configuration.deltaX = JBUI.unscale(new.x - original.x)
    configuration.deltaY = JBUI.unscale(new.y - original.y)
  }

  private fun calculateNewAlignmentAfterDrag(popupWindow: Window): PresentationAssistantPopupAlignment {
    val popupBounds = popupWindow.bounds
    val ideBounds = ideFrameScreenBounds(project)
    popupBounds.x -= ideBounds.x
    popupBounds.y -= ideBounds.y
    ideBounds.x = 0
    ideBounds.y = 0

    return if (popupBounds.x < ideBounds.width / 2) {
      if (popupBounds.y < ideBounds.height / 2) PresentationAssistantPopupAlignment.TOP_LEFT
      else PresentationAssistantPopupAlignment.BOTTOM_LEFT
    }
    else {
      if (popupBounds.y < ideBounds.height / 2) PresentationAssistantPopupAlignment.TOP_RIGHT
      else PresentationAssistantPopupAlignment.BOTTOM_RIGHT
    }
  }

  private fun ideFrameScreenBounds(project: Project): Rectangle {
    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val ideFrameBounds = ideFrame.component.visibleRect
    val ideFrameScreenLocation = RelativePoint(ideFrame.component, ideFrameBounds.location).screenPoint
    ideFrameBounds.location = ideFrameScreenLocation

    return ideFrameBounds
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
    Disposer.dispose(settingsButton)
  }

  fun canBeReused(size: Int): Boolean = size == actionBlocks.size && (phase == Phase.FADING_IN || phase == Phase.SHOWN)

  private fun getPopupWindows(): List<Window> = actionBlocks.mapNotNull { actionBlock ->
    getPopupWindow(actionBlock.popup)
  }

  private fun getPopupWindow(popup: JBPopup): Window? {
    if (popup.isDisposed) return null
    val window = SwingUtilities.windowForComponent(popup.content)
    if (window != null && window.isShowing) return window
    return null
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

  private fun updateForcedToBeShown() {
    forcedToBeShown = isPopupHovered || isSettingsButtonForcedToBeShown
  }

  private data class PopupLocationInfo(val popupLocation: RelativePoint, val groupSize: Dimension)

  private fun computeLocation(project: Project, index: Int?, ignoreDelta: Boolean = false): PopupLocationInfo {
    val preferredSizes = actionBlocks.map { it.panel.getFullSize() }
    val gap = JBUIScale.scale(appearance.spaceBetweenPopups)
    val popupGroupSize: Dimension = if (actionBlocks.isNotEmpty()) {
      val totalWidth = preferredSizes.sumOf { it.width } + (gap * (preferredSizes.size - 1))
      Dimension(totalWidth, preferredSizes.first().height)
    }
    else Dimension()

    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val visibleRect = ideFrame.component.visibleRect
    val margin = JBUIScale.scale(60)
    val deltaX = if (ignoreDelta) 0 else configuration.deltaX?.let { JBUIScale.scale(it) } ?: 0
    val deltaY = if (ignoreDelta) 0 else configuration.deltaY?.let { JBUIScale.scale(it) } ?: 0

    val x = when (configuration.horizontalAlignment) {
      0 -> visibleRect.x + margin
      1 -> visibleRect.x + (visibleRect.width - popupGroupSize.width) / 2
      else -> visibleRect.x + visibleRect.width - popupGroupSize.width - margin
    } + (index?.takeIf {
      0 < index && index <= actionBlocks.size
    }?.let {
      // Calculate X for particular popup
      (0..<index).map { preferredSizes[it].width }.reduce { total, width ->
        total + width
      } + gap * index
    } ?: 0) + deltaX

    val y = when (configuration.verticalAlignment) {
      0 -> visibleRect.y + margin
      else -> visibleRect.y + visibleRect.height - popupGroupSize.height - margin
    } + deltaY

    return PopupLocationInfo(RelativePoint(ideFrame.component, Point(x, y)), popupGroupSize)
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

  internal data class Appearance(val titleFontSize: Float,
                                 val subtitleFontSize: Float,
                                 val popupInsets: Insets,
                                 val subtitleHorizontalInset: Int,
                                 val spaceBetweenPopups: Int,
                                 val titleSubtitleGap: Int,
                                 val settingsButtonWidth: Int,
                                 val theme: PresentationAssistantTheme)

  companion object {
    @Suppress("UseDPIAwareInsets") // Values from insets will be scaled at the usage place
    private fun appearanceFromSize(popupSize: PresentationAssistantPopupSize,
                                   theme: PresentationAssistantTheme): Appearance = when(popupSize) {
      PresentationAssistantPopupSize.SMALL -> Appearance(22f,
                                                         12f,
                                                         Insets(6, 12, 6, 12),
                                                         2,
                                                         8,
                                                         1,
                                                         25,
                                                         theme)

      PresentationAssistantPopupSize.MEDIUM -> Appearance(32f,
                                                          13f,
                                                          Insets(6, 16, 8, 16),
                                                          2,
                                                          12,
                                                          -2,
                                                          30,
                                                          theme)

      PresentationAssistantPopupSize.LARGE -> Appearance(40f,
                                                         14f,
                                                         Insets(6, 16, 8, 16),
                                                         2,
                                                         12,
                                                         -2,
                                                         34,
                                                         theme)
    }

    /**
     * Do not set the border color in New UI Light themes on macOS.
     * Because otherwise the border will be painted by [com.intellij.ui.PopupBorder] and will be cut by the corners.
     * In other cases the border is painted correctly using [com.intellij.ui.WindowRoundedCornersManager]
     * or there are no rounded corners, and it is painted properly by [com.intellij.ui.PopupBorder].
     */
    fun ComponentPopupBuilder.setBorderColorIfNeeded(theme: PresentationAssistantTheme) {
      if (!ExperimentalUI.isNewUI() || !SystemInfo.isMac || !JBColor.isBright()) {
        setBorderColor(theme.border)
      }
    }
  }
}
