// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mouse

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.animation.JBAnimator
import com.intellij.util.animation.JBAnimatorHelper
import com.intellij.util.animation.animation
import com.intellij.util.animation.components.BezierPainter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.util.concurrent.TimeUnit

internal class MouseWheelSmoothScrollOptionsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val settings = UISettings.instance.state
    val points = settings.animatedScrollingCurvePoints
    val myBezierPainter = BezierPainterWithAnimation(
      (points shr 24 and 0xFF) / 200.0,
      (points shr 16 and 0xFF) / 200.0,
      (points shr 8 and 0xFF) / 200.0,
      (points and 0xFF) / 200.0
    ).apply {
      minimumSize = Dimension(300, 200)
    }
    val isPlaying = BooleanPropertyPredicate(false)

    dialog(
      title = IdeBundle.message("title.smooth.scrolling.options"),
      resizable = true,
      panel = panel {
        lateinit var c: Cell<JBCheckBox>
        row {
          c = checkBox(IdeBundle.message("checkbox.smooth.scrolling.animated"))
            .bindSelected(settings::animatedScrolling)
            .enabledIf(isPlaying.not())
        }
        row(IdeBundle.message("label.smooth.scrolling.duration")) {
            spinner(0..2000, 50)
              .bindIntValue(settings::animatedScrollingDuration)
              .enabledIf(c.selected.and(isPlaying.not()))
            label(IdeBundle.message("label.milliseconds"))
        }
        row {
          cell(myBezierPainter)
            .horizontalAlign(HorizontalAlign.FILL)
            .verticalAlign(VerticalAlign.FILL)
            .enabledIf(c.selected.and(isPlaying.not()))
        }
        row {
          text(IdeBundle.message("link.smooth.scrolling.play.curve.animation")) {
            isPlaying.set(true)
            myBezierPainter.startAnimation()
          }.visibleIf(isPlaying.not())
          text(IdeBundle.message("link.smooth.scrolling.stop.curve.animation")) {
            isPlaying.set(false)
            myBezierPainter.stopAnimation()
          }.visibleIf(isPlaying)
        }
        panel {
          row {
            checkBox(IdeBundle.message("checkbox.smooth.scrolling.enable.high.precision.timer")).also {
              val checkbox = it.component
              checkbox.addItemListener {
                JBAnimatorHelper.setAvailable(checkbox.isSelected)
                if (JBAnimatorHelper.isAvailable() && isPlaying.get()) {
                  JBAnimatorHelper.requestHighPrecisionTimer(myBezierPainter.animator)
                }
              }
            }.bindSelected(PropertyBinding(JBAnimatorHelper::isAvailable, JBAnimatorHelper::setAvailable))
            contextHelp(IdeBundle.message("checkbox.smooth.scrolling.enable.high.precision.timer.help"))
            rowComment(IdeBundle.message("checkbox.smooth.scrolling.enable.high.precision.timer.comments"))
          }
        }.visible(SystemInfoRt.isWindows)
      }
    ).also {
      Disposer.register(it.disposable, myBezierPainter)
    }.showAndGet().let {
      if (it) {
        val (x1, y1) = myBezierPainter.firstControlPoint
        val (x2, y2) = myBezierPainter.secondControlPoint
        var targetValue = 0
        targetValue += (x1 * 200).toInt() shl 24 and 0xFF000000.toInt()
        targetValue += (y1 * 200).toInt() shl 16 and 0xFF0000
        targetValue += (x2 * 200).toInt() shl 8 and 0xFF00
        targetValue += (y2 * 200).toInt() and 0xFF
        settings.animatedScrollingCurvePoints = targetValue
      }
    }
  }

  private class BooleanPropertyPredicate(value: Boolean) : ComponentPredicate() {
    private val property = AtomicBooleanProperty(value)

    fun set(value: Boolean) = property.set(value)

    fun get(): Boolean = property.get()

    override fun addListener(listener: (Boolean) -> Unit) {
      property.afterChange {
        listener(get())
      }
    }

    override fun invoke(): Boolean = get()
  }

  private operator fun Point2D.component1() = x
  private operator fun Point2D.component2() = y

  private class BezierPainterWithAnimation(x1: Double, y1: Double, x2: Double, y2: Double) : BezierPainter(x1, y1, x2, y2), Disposable {

    val animator = JBAnimator(JBAnimator.Thread.POOLED_THREAD, this).apply {
      period = 1
      isCyclic = true
      name = "Bezier Painter Animation Test"
    }
    private var animationId = -1L
    private var x = 0.0
    private var frames = mutableListOf<Long>()

    fun startAnimation() {
      animationId = animator.animate(
        animation {
          x = it
        }.apply {
          easing = this@BezierPainterWithAnimation.getEasing().freeze(0.0, 2.0 / 3)
          duration = 1000
          runWhenUpdated {
            repaint()
          }
          runWhenExpiredOrCancelled {
            x = 0.0
          }
        }
      )
    }

    fun stopAnimation() {
      animator.stop()
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (g !is Graphics2D) return

      val bounds = g.clipBounds
      if (animator.isRunning(animationId)) {
        val d = 5.0
        val shape = Path2D.Double()
        val x1 = 0.0
        val y1 = (1 - x) * bounds.height
        shape.moveTo(x1, y1)
        shape.lineTo(x1 + d, y1 - d)
        shape.lineTo(x1 + 3 * d, y1 - d)
        shape.lineTo(x1 + 3 * d, y1 + d)
        shape.lineTo(x1 + d, y1 + d)
        shape.closePath()
        g.color = JBColor.YELLOW
        g.fill(shape)

        val t = System.nanoTime()
        frames.add(t)
        val it = frames.iterator()
        while (t - it.next() > TimeUnit.SECONDS.toNanos(1)) {
          it.remove()
        }
      }

      if (frames.isNotEmpty()) {
        GraphicsUtil.setupAntialiasing(g)
        g.color = UIUtil.getLabelDisabledForeground()
        val b = bounds
        val text = IdeBundle.message("label.smooth.scrolling.bezier.panel.updates", frames.size)
        g.drawString(text, width / 16, b.height - 5)
      }
    }

    override fun dispose() {
    }
  }
}