// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel.MyInlineProgressIndicator
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.BalloonLayoutImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.Gray
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities

internal class ProcessBalloon(private val maxVisible: Int) {
  private val indicators = ArrayList<MyInlineProgressIndicator>()
  private var isVisible = 0

  fun addIndicator(pane: JRootPane?, indicator: MyInlineProgressIndicator) {
    if (pane != null) {
      indicators.add(indicator)
      show(pane)
    }
  }

  fun removeIndicator(pane: JRootPane?, indicator: MyInlineProgressIndicator) {
    indicators.remove(indicator)

    if (indicator.presentationModeProgressPanel != null) {
      isVisible--

      if (pane != null && !indicators.isEmpty()) {
        show(pane)
      }
    }
  }

  private fun show(pane: JRootPane) {
    val indicators = ArrayList<MyInlineProgressIndicator>()

    for (indicator in this.indicators) {
      if (indicator.presentationModeProgressPanel == null) {
        if (isVisible == maxVisible) {
          continue
        }

        isVisible++

        val presentationModeProgressPanel = PresentationModeProgressPanel(indicator)
        indicator.presentationModeProgressPanel = presentationModeProgressPanel
        indicator.updateProgressNow()

        indicator.presentationModeBalloon = create(pane, indicator, presentationModeProgressPanel.progressPanel)
        indicator.presentationModeShowBalloon = true

        indicators.add(indicator)
      }
      else if (indicator.presentationModeBalloon?.isDisposed == false) {
        indicators.add(indicator)
      }
    }

    for (indicator in indicators) {
      if (indicator.presentationModeShowBalloon) {
        indicator.presentationModeShowBalloon = false

        indicator.presentationModeBalloon!!.show(object : PositionTracker<Balloon>(getAnchor(pane)) {
          override fun recalculateLocation(balloon: Balloon): RelativePoint {
            val c = getAnchor(pane)
            var y = c.height - scale(45)

            val balloonLayout = getBalloonLayout(pane)
            if (balloonLayout != null && !isBottomSideToolWindowsVisible(pane)) {
              val component = balloonLayout.topBalloonComponent
              if (component != null) {
                y = SwingUtilities.convertPoint(component, 0, -scale(45), c).y
              }
            }

            if (isVisible > 1) {
              val index = this@ProcessBalloon.indicators.indexOf(indicator)
              val rowHeight = balloon.preferredSize.height + JBUI.scale(5)
              y -= rowHeight * (isVisible - index - 1)
            }

            return RelativePoint(c, Point(c.width - scale(150), y))
          }
        }, Balloon.Position.above)
      }
      else {
        indicator.presentationModeBalloon?.revalidate()
      }
    }
  }
}

private fun create(pane: JRootPane, parentDisposable: Disposable, content: JComponent): Balloon {
  content.putClientProperty(InfoAndProgressPanel.FAKE_BALLOON, Any())

  val balloon = JBPopupFactory.getInstance().createBalloonBuilder(content)
    .setFadeoutTime(0)
    .setFillColor(Gray.TRANSPARENT)
    .setShowCallout(false)
    .setBorderColor(Gray.TRANSPARENT)
    .setBorderInsets(JBInsets.emptyInsets())
    .setAnimationCycle(0)
    .setCloseButtonEnabled(false)
    .setHideOnClickOutside(false)
    .setDisposable(parentDisposable)
    .setHideOnFrameResize(false)
    .setHideOnKeyOutside(false)
    .setBlockClicksThroughBalloon(true)
    .setHideOnAction(false)
    .setShadow(false)
    .createBalloon()

  val balloonLayout = getBalloonLayout(pane) ?: return balloon
  balloon.addListener(object : JBPopupListener, Runnable {
    override fun beforeShown(event: LightweightWindowEvent) {
      balloonLayout.addListener(this)
    }

    override fun onClosed(event: LightweightWindowEvent) {
      balloonLayout.removeListener(this)
    }

    override fun run() {
      if (!balloon.isDisposed) {
        balloon.revalidate()
      }
    }
  })
  return balloon
}

private fun getBalloonLayout(pane: JRootPane): BalloonLayoutImpl? {
  val parent = ComponentUtil.findUltimateParent(pane)
  return if (parent is IdeFrame) parent.balloonLayout as? BalloonLayoutImpl else null
}

private fun getAnchor(pane: JRootPane): Component {
  val splitters = UIUtil.findComponentOfType(pane, EditorsSplitters::class.java)
  if (splitters != null && splitters.isShowing) {
    return splitters
  }

  val tabWrapper: Component? = UIUtil.findComponentOfType(pane, TabbedPaneWrapper.TabWrapper::class.java)
  if (tabWrapper != null && tabWrapper.isShowing) {
    return tabWrapper
  }
  return pane
}

private fun isBottomSideToolWindowsVisible(parent: JRootPane): Boolean {
  val pane = UIUtil.findComponentOfType(parent, ToolWindowPane::class.java)
  return pane != null && pane.isBottomSideToolWindowsVisible
}