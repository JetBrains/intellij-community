// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.util.ThreeState
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicIntegerArray
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

internal class WriteThreadIndicatorWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = UIBundle.message("status.bar.write.thread.widget.name")

  override fun isAvailable(project: Project): Boolean {
    val app = ApplicationManager.getApplication()
    return app.isInternal && app is ApplicationImpl
  }

  override fun createWidget(project: Project): StatusBarWidget = WriteThreadWidget()

  override fun isConfigurable(): Boolean = ApplicationManager.getApplication().isInternal

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = ApplicationManager.getApplication().isInternal

  override fun isEnabledByDefault(): Boolean = false
}

private const val ID = "WriteThread"
private val WIDGET_SIZE = Dimension(100, 20)

private class WriteThreadWidget : CustomStatusBarWidget {
  private var component: JPanel? = null
  private val statsDeque = LinkedBlockingDeque<AtomicIntegerArray>()

  @Volatile
  private var currentStats = AtomicIntegerArray(4)

  init {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    app.getCoroutineScope().launch {
      while (true) {
        delay(1.milliseconds)

        val currentValue = app.isCurrentWriteOnEdt
        val currentStats = currentStats
        currentStats.incrementAndGet((if (currentValue) ThreeState.YES else ThreeState.NO).ordinal)
        currentStats.incrementAndGet(3)
      }
    }.cancelOnDispose(this)
  }

  override fun getComponent(): JComponent {
    if (component == null) {
      component = MyComponent()
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        while (true) {
          delay(500.milliseconds)

          statsDeque.add(currentStats)
          while (statsDeque.size > WIDGET_SIZE.width) {
            statsDeque.pop()
          }
          currentStats = AtomicIntegerArray(4)
          component?.repaint()
        }
      }.cancelOnDispose(this)
    }
    return component!!
  }

  override fun ID(): String = ID

  private inner class MyComponent : JPanel() {
    override fun getPreferredSize(): Dimension = WIDGET_SIZE

    override fun getMinimumSize(): Dimension = WIDGET_SIZE

    override fun getMaximumSize(): Dimension = WIDGET_SIZE

    override fun paint(g: Graphics) {
      super.paint(g)
      if (g !is Graphics2D) {
        return
      }

      for ((xOffset, stats) in statsDeque.withIndex()) {
        g.color = JBColor.GRAY
        g.fillRect(xOffset, 0, 1, WIDGET_SIZE.height)
        val sum = stats[3]
        if (sum <= 0) {
          continue
        }

        var yOffset = 0
        g.color = JBColor.RED
        var height = (stats[0] * WIDGET_SIZE.height + sum - 1) / sum
        @Suppress("KotlinConstantConditions")
        g.fillRect(xOffset, WIDGET_SIZE.height - yOffset - height, 1, height)
        yOffset -= height
        g.color = JBColor.GREEN
        height = (stats[1] * WIDGET_SIZE.height + sum - 1) / sum
        g.fillRect(xOffset, WIDGET_SIZE.height - yOffset - height, 1, height)
      }
    }
  }
}