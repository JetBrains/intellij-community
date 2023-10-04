// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGapsX
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

internal class ActionInfoPanel(project: Project, textFragments: List<TextData>) : NonOpaquePanel(BorderLayout()), Disposable {
  private val hint: JBPopup
  private val labelsPanel: JPanel = NonOpaquePanel(GridLayout()).apply { background = null }
  private val hideAlarm = Alarm(this)
  private var animator: Animator
  private var phase = Phase.FADING_IN
  private val hintAlpha = if (StartupUiUtil.isDarkTheme) 0.05.toFloat() else 0.1.toFloat()
  private val configuration = PresentationAssistant.INSTANCE.configuration

  enum class Phase { FADING_IN, SHOWN, FADING_OUT, HIDDEN }

  init {
    updateLabelText(project, textFragments)
    isOpaque = true
    background = null
    add(labelsPanel, BorderLayout.CENTER)

    hint = with(JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)) {
      setAlpha(1.0.toFloat())
      setFocusable(false)
      setBelongsToGlobalPopupStack(false)
      setCancelKeyEnabled(false)
      setCancelCallback { phase = Phase.HIDDEN; true }
      createPopup()
    }
    hint.addListener(object : JBPopupListener {
      override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}
      override fun onClosed(lightweightWindowEvent: LightweightWindowEvent) {
        phase = Phase.HIDDEN
      }
    })

    animator = FadeInOutAnimator(true)
    hint.show(computeLocation(project))
    animator.resume()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) hint.pack(true, true)
  }

  private fun fadeOut() {
    if (phase != Phase.SHOWN) return
    phase = Phase.FADING_OUT
    Disposer.dispose(animator)
    animator = FadeInOutAnimator(false)
    animator.resume()
  }

  inner class FadeInOutAnimator(private val forward: Boolean) : Animator("Action Hint Fade In/Out", 5, 100, false, forward) {
    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      if (forward && phase != Phase.FADING_IN
          || !forward && phase != Phase.FADING_OUT) return
      setAlpha(hintAlpha + (1 - hintAlpha) * (totalFrames - frame) / totalFrames)
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

  private fun getHintWindow(): Window? {
    if (hint.isDisposed) return null
    val window = SwingUtilities.windowForComponent(hint.content)
    if (window != null && window.isShowing) return window
    return null
  }

  private fun setAlpha(alpha: Float) {
    val window = getHintWindow()
    if (window != null) {
      WindowManager.getInstance().setAlphaModeRatio(window, alpha)
    }
  }

  private fun showFinal() {
    phase = Phase.SHOWN
    setAlpha(hintAlpha)
    hideAlarm.cancelAllRequests()
    hideAlarm.addRequest({ fadeOut() }, configuration.popupDuration, ModalityState.any())
  }

  fun updateText(project: Project, textFragments: List<TextData>) {
    if (getHintWindow() == null) return
    labelsPanel.removeAll()
    updateLabelText(project, textFragments)
    hint.setLocation(computeLocation(project).screenPoint)
    hint.size = preferredSize
    hint.content.validate()
    hint.content.repaint()
    showFinal()
  }

  private fun computeLocation(project: Project): RelativePoint {
    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val statusBarHeight = ideFrame.statusBar?.component?.height ?: 0
    val visibleRect = ideFrame.component.visibleRect
    val popupSize = preferredSize
    val x = when (configuration.horizontalAlignment) {
      0 -> visibleRect.x + configuration.margin
      1 -> visibleRect.x + (visibleRect.width - popupSize.width) / 2
      else -> visibleRect.x + visibleRect.width - popupSize.width - configuration.margin
    }
    val y = when (configuration.verticalAlignment) {
      0 -> visibleRect.y + configuration.margin
      else -> visibleRect.y + visibleRect.height - popupSize.height - statusBarHeight - configuration.margin
    }
    return RelativePoint(ideFrame.component, Point(x, y))
  }

  private fun updateLabelText(project: Project, textFragments: List<TextData>) {
    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val blocks = createBlocks(textFragments, ideFrame)
    labelsPanel.addComponentsWithGap(blocks, 12)
  }

  private fun List<Pair<String, Font?>>.mergeFragments(): List<Pair<String, Font?>> {
    val result = ArrayList<Pair<String, Font?>>()
    for (item in this) {
      val last = result.lastOrNull()
      if (last != null && last.second == item.second) {
        result.removeAt(result.lastIndex)
        result.add(Pair(last.first + item.first, last.second))
      }
      else {
        result.add(item)
      }
    }
    return result
  }

  private fun createBlocks(textFragments: List<TextData>, ideFrame: IdeFrame): List<ActionInfoBlockPanel> {
    return textFragments.map { ActionInfoBlockPanel(it) }
  }

  private fun createLabels(textFragments: List<Pair<String, Font?>>, ideFrame: IdeFrame): List<JLabel> {
    var fontSize = configuration.fontSize.toFloat()
    val color = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
    val labels = textFragments.mergeFragments().map {
      @Suppress("HardCodedStringLiteral")
      val label = JLabel("<html>${it.first}</html>", SwingConstants.CENTER)
      label.foreground = color
      if (it.second != null) label.font = it.second
      label
    }

    fun setFontSize(size: Float) {
      for (label in labels) {
        label.font = label.font.deriveFont(size)
      }
      val maxAscent = labels.maxOfOrNull { it.getFontMetrics(it.font).maxAscent } ?: 0
      for (label in labels) {
        val ascent = label.getFontMetrics(label.font).maxAscent
        if (ascent < maxAscent) {
          label.border = BorderFactory.createEmptyBorder(maxAscent - ascent, 0, 0, 0)
        }
        else {
          label.border = null
        }
      }
    }
    setFontSize(fontSize)
    val frameWidth = ideFrame.component.width
    if (frameWidth > 100) {
      while (labels.sumOf { it.preferredSize.width } > frameWidth - 10 && fontSize > 12) {
        setFontSize(--fontSize)
      }
    }
    return labels
  }

  fun close() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    phase = Phase.HIDDEN
    if (!hint.isDisposed) {
      hint.cancel()
    }
    Disposer.dispose(animator)
  }

  fun canBeReused(): Boolean = phase == Phase.FADING_IN || phase == Phase.SHOWN
}

internal class ActionInfoBlockPanel(val textData: TextData) : JPanel() {
  private val titleLabel = JBLabel()
  private val subtitleLabel = JBLabel()

  init {
    layout = GridLayout()
    RowsGridBuilder(this)
      .row(resizable = true).cell(component = titleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)
      .row(resizable = true).cell(component = subtitleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)

    titleLabel.border = JBEmptyBorder(6, 16, 0, 16)
    subtitleLabel.border = JBEmptyBorder(0, 18, 8, 16)
    subtitleLabel.font = JBFont.label().deriveFont(JBUIScale.scale(SUBTITLE_FONT_SIZE))

    updateLabels()
  }

  override fun paintComponent(g: Graphics?) {
    if (g !is Graphics2D) return
    super.paintComponent(g)

    g.color = BACKGROUND
    val config = GraphicsUtil.setupAAPainting(g)
    val scale = (g.transform.scaleX * JBUIScale.scale(1)).toInt()
    g.fillRoundRect(0, 0, width, height, CORNER_RADIUS * scale, CORNER_RADIUS * scale)
    config.restore()
  }

  private fun updateLabels() {
    titleLabel.text = "<html>${textData.title}</html>"
    titleLabel.font = (font?.let { JBFont.create(it) } ?: JBFont.label()).deriveFont(JBUIScale.scale(TITLE_FONT_SIZE))

    val subtitle = textData.subtitle
    subtitleLabel.text = subtitle?.let { "<html>${subtitle}</html>" } ?: " "
  }

  companion object {
    private const val TITLE_FONT_SIZE = 40f
    private const val SUBTITLE_FONT_SIZE = 14f
    private const val CORNER_RADIUS = 8
    private val BACKGROUND = EditorColorsManager.getInstance().globalScheme.getColor(BACKGROUND_COLOR_KEY)
  }
}

private fun JComponent.addComponentsWithGap(components: List<JComponent>, gap: Int) {
  val builder = RowsGridBuilder(this)
  val row = builder.row(resizable = true)
  for (c in components) {
    row.cell(c)
  }

  row.columnsGaps((0 until row.columnsCount).map {
    val rightGap = if (it == (row.columnsCount - 1)) 0 else gap
    UnscaledGapsX(0, rightGap)
  })
}
