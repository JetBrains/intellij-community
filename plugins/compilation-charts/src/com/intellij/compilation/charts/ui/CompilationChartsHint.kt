// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.CompilationChartsBundle
import com.intellij.compilation.charts.ui.CompilationChartsAction.Position.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class CompilationChartsHint(
  private val project: Project,
  private val component: CompilationChartsDiagramsComponent,
  disposable: Disposable,
) : Disposable {
  private var module: ModuleIndex? = null
  private var active: Boolean = false
  private var balloon: Balloon? = null
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
  private var balloonRectOnScreen: Rectangle? = null

  fun module(): ModuleIndex? = module
  fun isActive(): Boolean = active

  fun open(module: ModuleIndex, event: MouseEvent, delayMs: Int = 350) {
    close()
    this.module = module
    this.active = true

    alarm.cancelAllRequests()
    alarm.addRequest({if (active && this.module == module) showBalloon(module, event) }, delayMs)
  }

  private fun showBalloon(module: ModuleIndex, event: MouseEvent) {
    val actions = listOf(
      OpenDirectoryAction(project, module.key) { close() },
      OpenProjectStructureAction(project, module.key.name) { close() },
      ShowModuleDependenciesAction(project, module.key.name, component) { close() },
      ShowMatrixDependenciesAction(project, module.key.name, component) { close() }
    )

    val content = content(module, actions)
    val relativePoint = RelativePoint(component, event.point)

    balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(content)
      .setHideOnClickOutside(true)
      .setHideOnKeyOutside(true)
      .setHideOnAction(true)
      .setHideOnCloseClick(true)
      .setFadeoutTime(0)
      .setAnimationCycle(200)
      .setDisposable(this)
      .createBalloon().apply {

        addListener(object : JBPopupListener {
          override fun beforeShown(e: LightweightWindowEvent) {
            SwingUtilities.invokeLater {
              val p = Point(0, 0)
              SwingUtilities.convertPointToScreen(p, content)
              val size = content.size
              val border = 10
              balloonRectOnScreen = Rectangle(p.x - border,
                                              p.y - border,
                                              size.width + 2 * border,
                                              size.height + 2 * border)
            }
          }

          override fun onClosed(e: LightweightWindowEvent) {
            this@CompilationChartsHint.active = false
            this@CompilationChartsHint.module = null
            this@CompilationChartsHint.balloon = null
            this@CompilationChartsHint.balloonRectOnScreen = null
            this@CompilationChartsHint.cancelHint()
          }
        })

        show(relativePoint, Balloon.Position.below)
      }
  }

  fun isInside(point: Point): Boolean {
    val balloonRect = balloonRectOnScreen ?: return false
    val border = 10
    val expandedRect = Rectangle(balloonRect)
    expandedRect.grow(border, border)

    val screenX = component.locationOnScreen.x + point.x
    val screenY = component.locationOnScreen.y + point.y

    return expandedRect.contains(screenX, screenY)
  }

  fun close() {
    balloon?.hide()
    cancelHint()
  }

  private fun content(module: ModuleIndex, actions: List<CompilationChartsAction>): JComponent = panel {
    row {
      actions.filter { it.isAccessible() && it.position() == LEFT }
        .forEach { it.draw(this) }
      label(module.key.name)
      actions.filter { it.isAccessible() && it.position() == RIGHT }
        .forEach { it.draw(this) }
    }
    separator()
    row { label(CompilationChartsBundle.message("charts.duration", module.info["duration"])) }
    actions.filter { it.isAccessible() && it.position() == LIST }
      .forEach { row { it.draw(this) } }
  }.apply {
    border = JBUI.Borders.empty(10)
  }

  private fun cancelHint() {
    alarm.cancelAllRequests()
  }

  override fun dispose() {
    close()
  }
}
