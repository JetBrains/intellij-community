// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.Gray
import com.intellij.ui.IslandsState
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.awt.Color
import java.awt.Graphics
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

private const val ID = "EditorAnimationCacheStatistics"

private const val WINDOW_SECONDS = STATISTICS_BUCKET_MS * STATISTICS_BUCKET_COUNT / 1000

internal class EditorAnimationCacheStatisticsWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = UIBundle.message("status.bar.editor.animation.cache.widget.name")

  override fun isEnabledByDefault(): Boolean = false

  override fun isAvailable(project: Project): Boolean = isInternalMode()

  override fun createWidget(project: Project): StatusBarWidget = EditorAnimationCacheStatisticsWidget()

  override fun isConfigurable(): Boolean = isInternalMode()

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = isInternalMode()
}

private fun isInternalMode(): Boolean = ApplicationManager.getApplication().isInternal

private class EditorAnimationCacheStatisticsWidget : CustomStatusBarWidget {
  private val lazyUi = lazy(::EditorAnimationCacheStatisticsUi)

  @get:RequiresEdt
  private val ui: EditorAnimationCacheStatisticsUi
    get() = lazyUi.value

  override fun ID(): String = ID

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

  override fun getComponent(): JComponent = ui.component

  override fun dispose() {
    if (lazyUi.isInitialized()) {
      lazyUi.value.dispose()
    }
  }
}

private class EditorAnimationCacheStatisticsUi {
  private val bar = HitRateBar()
  private val updates: Job = bar.launchOnShow(ID) {
    while (true) {
      bar.updateState()
      delay(STATISTICS_BUCKET_MS.milliseconds)
    }
  }

  val component: JComponent = bar

  fun dispose() {
    updates.cancel()
  }
}

private class HitRateBar : TextPanel() {
  private val servedColor = JBColor.namedColor("MemoryIndicator.usedBackground", JBColor(Gray._185, Gray._110))
  private val trackColor = JBColor.namedColor("MemoryIndicator.allocatedBackground", JBColor(Gray._215, Gray._90))

  private var rate: CacheHitRate? = null

  init {
    isFocusable = false
    setTextAlignment(CENTER_ALIGNMENT)
    border = JBUI.Borders.empty(0, 2)
    updateUI()
  }

  override fun getBackground(): Color? = null

  override val textForPreferredSize: String
    get() = " " + UIBundle.message("status.bar.editor.animation.cache.widget.text", 100)

  fun updateState() {
    if (!isShowing) return

    val hitRate = EditorAnimationCacheStatistics.hitRate()
    if (hitRate == rate && text != null) return

    rate = hitRate
    text = when (hitRate) {
      null -> UIBundle.message("status.bar.editor.animation.cache.widget.idle")
      else -> UIBundle.message("status.bar.editor.animation.cache.widget.text", hitRate.hitPercent)
    }
    setToolTipText(HtmlChunk.text(when (hitRate) {
      null -> UIBundle.message("status.bar.editor.animation.cache.widget.tooltip.idle", WINDOW_SECONDS)
      else -> UIBundle.message(
        "status.bar.editor.animation.cache.widget.tooltip",
        hitRate.hits,
        hitRate.misses,
        WINDOW_SECONDS,
      )
    }))
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    val size = size
    val servedWidth = size.width * (rate?.hitPercent ?: 0) / 100

    val isIslandTheme = IslandsState.isEnabled()
    val arc = if (isIslandTheme) JBUI.scale(6) else 0
    val yOffset = if (isIslandTheme) JBUI.scale(3) else 0
    val hDelta = if (isIslandTheme) JBUI.scale(8) else 0

    val config = GraphicsUtil.setupAAPainting(g)
    g.color = UIUtil.getPanelBackground()
    g.fillRoundRect(0, yOffset, size.width, size.height - hDelta, arc, arc)

    g.color = trackColor
    g.fillRoundRect(0, yOffset, size.width, size.height - hDelta, arc, arc)

    g.color = servedColor
    g.fillRoundRect(0, yOffset, servedWidth, size.height - hDelta, arc, arc)
    config.restore()

    super.paintComponent(g)
  }
}
