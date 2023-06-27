// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.status.ClockPanel
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.Animator
import com.intellij.util.ui.MouseEventAdapter
import com.intellij.util.ui.TimerUtil
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.GeneralPath
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import kotlin.math.cos
import kotlin.math.sqrt

internal class FloatingMenuBarFlavor(private val menuBar: IdeMenuBar) : IdeMenuFlavor {
  private val clockPanel = ClockPanel()
  private val button = MyExitFullScreenButton()
  private val animator = MyAnimator(menuBar = menuBar, flavor = this)

  private val activationWatcher = TimerUtil.createNamedTimer("IdeMenuBar", 100, MyActionListener())

  override var state: IdeMenuBar.State = IdeMenuBar.State.EXPANDED
    set(value) {
      field = value
      if (value == IdeMenuBar.State.EXPANDING && !activationWatcher.isRunning) {
        activationWatcher.start()
      }
      else if (activationWatcher.isRunning && (value == IdeMenuBar.State.EXPANDED || value == IdeMenuBar.State.COLLAPSED)) {
        activationWatcher.stop()
      }
    }

  init {
    menuBar.add(clockPanel)
    menuBar.add(button)

    menuBar.addMouseListener(MyMouseListener())
    menuBar.addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) { updateState() }
  }

  private inner class MyActionListener : ActionListener {
    override fun actionPerformed(e: ActionEvent) {
      if (state == IdeMenuBar.State.EXPANDED || state == IdeMenuBar.State.EXPANDING) {
        return
      }

      val activated: Boolean = menuBar.isActivated
      if (menuBar.activated && !activated && state == IdeMenuBar.State.TEMPORARY_EXPANDED) {
        menuBar.activated = false
        state = IdeMenuBar.State.COLLAPSING
        restartAnimator()
      }
      if (activated) {
        menuBar.activated = true
      }
    }
  }

  private class MyAnimator(private val menuBar: JComponent,
                           private val flavor: FloatingMenuBarFlavor) : Animator("MenuBarAnimator", 16, 300, false) {
    @JvmField var progress: Double = 0.0

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      progress = (1 - cos(Math.PI * (frame.toFloat() / totalFrames))) / 2
      menuBar.revalidate()
      menuBar.repaint()
    }

    override fun paintCycleEnd() {
      progress = 1.0
      when (flavor.state) {
        IdeMenuBar.State.COLLAPSING -> flavor.state = IdeMenuBar.State.COLLAPSED
        IdeMenuBar.State.EXPANDING -> flavor.state = IdeMenuBar.State.TEMPORARY_EXPANDED
        else -> {}
      }
      if (!menuBar.isShowing) {
        return
      }

      menuBar.revalidate()
      if (flavor.state == IdeMenuBar.State.COLLAPSED) {
        // we should repaint the parent, to clear 1px on top when a menu is collapsed
        menuBar.parent.repaint()
      }
      else {
        menuBar.repaint()
      }
    }
  }

  override fun getProgress(): Double = animator.progress

  override fun suspendAnimator() {
    animator.suspend()
  }

  private fun updateState() {
    val window = (SwingUtilities.getWindowAncestor(menuBar) as? IdeFrame) ?: return
    if (window.isInFullScreen) {
      state = IdeMenuBar.State.COLLAPSING
      restartAnimator()
    }
    else {
      animator.suspend()
      state = IdeMenuBar.State.EXPANDED
      clockPanel.isVisible = false
      button.isVisible = false
    }
  }

  override fun restartAnimator() {
    animator.reset()
    animator.resume()
  }

  override fun addClockPanel() {
    // why do we add it if we already add it?
    menuBar.add(clockPanel)
    menuBar.add(button)
  }

  override fun updateAppMenu() {
    doUpdateAppMenu()
  }

  override fun layoutClockPanelAndButton() {
    if (state == IdeMenuBar.State.EXPANDED) {
      clockPanel.isVisible = false
      button.isVisible = false
    }
    else {
      clockPanel.isVisible = true
      button.isVisible = true
      var preferredSize = button.preferredSize
      button.setBounds(menuBar.bounds.width - preferredSize.width, 0, preferredSize.width, preferredSize.height)
      preferredSize = clockPanel.preferredSize
      clockPanel.setBounds(menuBar.bounds.width - preferredSize.width - button.width, 0, preferredSize.width, preferredSize.height)
    }
  }

  override fun correctMenuCount(menuCount: Int): Int {
    return menuCount - 2
  }
}

private class MyMouseListener : MouseAdapter() {
  override fun mousePressed(e: MouseEvent) {
    val c = e.component
    if (c !is IdeMenuBar) {
      return
    }

    val size = c.getSize()
    val insets = c.insets
    val p = e.point
    if (p.y < insets.top || p.y >= size.height - insets.bottom) {
      val item = c.findComponentAt(p.x, size.height / 2)
      if (item is JMenuItem) {
        // re-target border clicks as a menu item ones
        item.dispatchEvent(MouseEventAdapter.convert(e, item, 1, 1))
        e.consume()
      }
    }
  }
}

private class MyExitFullScreenButton : JButton() {
  init {
    isFocusable = false
    addActionListener {
      ProjectFrameHelper.getFrameHelper(SwingUtilities.getWindowAncestor(this))?.toggleFullScreen(false)
    }
    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        model.isRollover = true
      }

      override fun mouseExited(e: MouseEvent) {
        model.isRollover = false
      }
    })
  }

  override fun getPreferredSize(): Dimension {
    val height: Int
    val parent = parent
    height = if (isVisible && parent != null) {
      parent.size.height - parent.insets.top - parent.insets.bottom
    }
    else {
      super.getPreferredSize().height
    }
    return Dimension(height, height)
  }

  override fun getMaximumSize(): Dimension = preferredSize

  override fun paint(g: Graphics) {
    val g2d = g.create() as Graphics2D
    try {
      g2d.color = UIManager.getColor("Label.background")
      g2d.fillRect(0, 0, width + 1, height + 1)
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      val s = height.toDouble() / 13
      g2d.translate(s, s)
      val plate: Shape = RoundRectangle2D.Double(0.0, 0.0, s * 11, s * 11, s, s)
      val color = UIManager.getColor("Label.foreground")
      val hover = model.isRollover || model.isPressed
      g2d.color = ColorUtil.withAlpha(color, if (hover) .25 else .18)
      g2d.fill(plate)
      g2d.color = ColorUtil.withAlpha(color, if (hover) .4 else .33)
      g2d.draw(plate)
      g2d.color = ColorUtil.withAlpha(color, if (hover) .7 else .66)
      var path = GeneralPath()
      path.moveTo(s * 2, s * 6)
      path.lineTo(s * 5, s * 6)
      path.lineTo(s * 5, s * 9)
      path.lineTo(s * 4, s * 8)
      path.lineTo(s * 2, s * 10)
      path.quadTo(s * 2 - s / sqrt(2.0), s * 9 + s / sqrt(2.0), s, s * 9)
      path.lineTo(s * 3, s * 7)
      path.lineTo(s * 2, s * 6)
      path.closePath()
      g2d.fill(path)
      g2d.draw(path)
      path = GeneralPath()
      path.moveTo(s * 6, s * 2)
      path.lineTo(s * 6, s * 5)
      path.lineTo(s * 9, s * 5)
      path.lineTo(s * 8, s * 4)
      path.lineTo(s * 10, s * 2)
      path.quadTo(s * 9 + s / sqrt(2.0), s * 2 - s / sqrt(2.0), s * 9, s)
      path.lineTo(s * 7, s * 3)
      path.lineTo(s * 6, s * 2)
      path.closePath()
      g2d.fill(path)
      g2d.draw(path)
    }
    finally {
      g2d.dispose()
    }
  }
}