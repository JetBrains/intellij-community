// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.wm.impl

import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.toolWindow.ToolWindowEntry
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.ui.BalloonImpl
import com.intellij.ui.HintHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

internal class ToolWindowManagerNotifications(
  private val manager: ToolWindowManagerImpl,
) {
  fun canShowNotification(toolWindowId: String): Boolean {
    val readOnlyWindowInfo = manager.idToEntry[toolWindowId]?.readOnlyWindowInfo
    val anchor = readOnlyWindowInfo?.anchor ?: return false
    val isSplit = readOnlyWindowInfo.isSplit
    return manager.toolWindowPanes.values.firstNotNullOfOrNull {
      it.buttonManager.getStripeFor(anchor, isSplit).getButtonFor(toolWindowId)
    } != null
  }

  fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    if (manager.isNewUi) {
      notifySquareButtonByBalloon(options)
      return
    }

    val entry = manager.idToEntry[options.toolWindowId]!!
    entry.balloon?.let {
      Disposer.dispose(it)
    }

    val toolWindowPane = manager.getToolWindowPane(entry.toolWindow)
    val stripe = toolWindowPane.buttonManager.getStripeFor(entry.readOnlyWindowInfo.anchor, entry.readOnlyWindowInfo.isSplit)
    if (!entry.toolWindow.isAvailable) {
      entry.toolWindow.isPlaceholderMode = true
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
    }

    val anchor = entry.readOnlyWindowInfo.anchor
    val position = Ref(Balloon.Position.below)
    when (anchor) {
      ToolWindowAnchor.TOP -> position.set(Balloon.Position.below)
      ToolWindowAnchor.BOTTOM -> position.set(Balloon.Position.above)
      ToolWindowAnchor.LEFT -> position.set(Balloon.Position.atRight)
      ToolWindowAnchor.RIGHT -> position.set(Balloon.Position.atLeft)
    }

    if (!entry.readOnlyWindowInfo.isVisible) {
      manager.toolWindowAvailable(entry.toolWindow)
    }

    val balloon = createBalloon(options, entry)
    val button = stripe.getButtonFor(options.toolWindowId)?.getComponent()

    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (entry.toolWindow.isVisible &&
          (entry.toolWindow.type == ToolWindowType.WINDOWED ||
           entry.toolWindow.type == ToolWindowType.FLOATING)) {
        tracker = createPositionTracker(entry.toolWindow.component, ToolWindowAnchor.BOTTOM)
      }
      else if (button == null || !button.isShowing) {
        tracker = createPositionTracker(toolWindowPane, anchor)
      }
      else {
        tracker = object : PositionTracker<Balloon>(button) {
          override fun recalculateLocation(b: Balloon): RelativePoint? {
            val otherEntry = manager.idToEntry[options.toolWindowId] ?: return null
            val stripeButton = otherEntry.stripeButton
            if (stripeButton == null || otherEntry.readOnlyWindowInfo.anchor != anchor) {
              b.hide()
              return null
            }
            val component = stripeButton.getComponent()
            return RelativePoint(component, Point(component.bounds.width / 2, component.height / 2 - 2))
          }
        }
      }
      if (!balloon.isDisposed) {
        balloon.show(tracker, position.get())
      }
    }

    if (button != null && button.isValid) {
      show.run()
    }
    else {
      SwingUtilities.invokeLater(show)
    }
  }

  fun getToolWindowButton(toolWindowId: String): JComponent? {
    val entry = manager.idToEntry[toolWindowId] ?: return null
    return findBallonAlignment(entry, toolWindowId).first
  }

  fun closeBalloons() {
    for (entry in manager.idToEntry.values) {
      entry.balloon?.hideImmediately()
    }
  }

  fun getToolWindowBalloon(id: String): Balloon? = manager.idToEntry[id]?.balloon

  private fun notifySquareButtonByBalloon(options: ToolWindowBalloonShowOptions) {
    val entry = manager.idToEntry[options.toolWindowId]!!
    entry.balloon?.let(Disposer::dispose)

    val (button, position) = findBallonAlignment(entry, options.toolWindowId)

    val anchor = entry.readOnlyWindowInfo.anchor
    val balloon = createBalloon(options, entry)
    val toolWindowPane = manager.getToolWindowPane(entry.readOnlyWindowInfo.safeToolWindowPaneId)
    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (entry.toolWindow.isVisible &&
          (entry.toolWindow.type == ToolWindowType.WINDOWED ||
           entry.toolWindow.type == ToolWindowType.FLOATING)) {
        tracker = createPositionTracker(entry.toolWindow.component, ToolWindowAnchor.BOTTOM)
      }
      else if (!button.isShowing) {
        tracker = createPositionTracker(toolWindowPane, anchor)
        if (balloon is BalloonImpl) {
          balloon.setShowPointer(false)
        }
      }
      else {
        tracker = object : PositionTracker<Balloon>(button) {
          override fun recalculateLocation(balloon: Balloon): RelativePoint? {
            val otherEntry = manager.idToEntry[options.toolWindowId] ?: return null
            if (otherEntry.readOnlyWindowInfo.anchor != anchor) {
              balloon.hide()
              return null
            }

            return RelativePoint(button,
                                 Point(if (position == Balloon.Position.atRight) 0 else button.bounds.width, button.height / 2))
          }
        }
      }
      if (!balloon.isDisposed) {
        balloon.show(tracker, position)
      }
    }

    if (button.isValid) {
      show.run()
    }
    else {
      SwingUtilities.invokeLater(show)
    }
  }

  private fun findBallonAlignment(
    entry: ToolWindowEntry,
    toolWindowId: String,
  ): Pair<JComponent, Balloon.Position> {
    val anchor = entry.readOnlyWindowInfo.anchor
    var position = when (anchor) {
      ToolWindowAnchor.TOP -> Balloon.Position.atRight
      ToolWindowAnchor.RIGHT -> Balloon.Position.atRight
      ToolWindowAnchor.BOTTOM -> Balloon.Position.atLeft
      ToolWindowAnchor.LEFT -> Balloon.Position.atLeft
      else -> Balloon.Position.atLeft
    }

    val toolWindowPane = manager.getToolWindowPane(entry.readOnlyWindowInfo.safeToolWindowPaneId)
    val buttonManager = toolWindowPane.buttonManager as ToolWindowPaneNewButtonManager
    var button = buttonManager.getSquareStripeFor(anchor).getButtonFor(toolWindowId)?.getComponent()
    if (button == null && anchor == ToolWindowAnchor.BOTTOM) {
      button = buttonManager.getSquareStripeFor(ToolWindowAnchor.RIGHT).getButtonFor(toolWindowId)?.getComponent()
      if (button != null && button.isShowing) {
        position = Balloon.Position.atRight
      }
    }
    if (button == null || !button.isShowing) {
      button = buttonManager.getMoreButton(manager.getMoreButtonSide())
      position = Balloon.Position.atLeft
    }

    return Pair(button, position)
  }

  private fun createPositionTracker(component: Component, anchor: ToolWindowAnchor): PositionTracker<Balloon> {
    return object : PositionTracker<Balloon>(component) {
      override fun recalculateLocation(balloon: Balloon): RelativePoint {
        val bounds = component.bounds
        val target = StartupUiUtil.getCenterPoint(bounds, Dimension(1, 1))
        when (anchor) {
          ToolWindowAnchor.TOP -> target.y = 0
          ToolWindowAnchor.BOTTOM -> target.y = bounds.height - 3
          ToolWindowAnchor.LEFT -> target.x = 0
          ToolWindowAnchor.RIGHT -> target.x = bounds.width
        }
        return RelativePoint(component, target)
      }
    }
  }

  private fun createBalloon(options: ToolWindowBalloonShowOptions, entry: ToolWindowEntry): Balloon {
    val listenerWrapper = BalloonHyperlinkListener(options.listener)

    var foreground = options.type.titleForeground
    var background = options.type.popupBackground
    var borderColor = options.type.borderColor
    if (manager.isNewUi && options.type === MessageType.INFO) {
      foreground = HintHint.Status.Info.foreground
      background = HintHint.Status.Info.background
      borderColor = HintHint.Status.Info.border
    }

    @Suppress("HardCodedStringLiteral")
    val content = options.htmlBody.replace("\n", "<br>")
    val balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(content, options.icon, foreground, background, listenerWrapper)
      .setBorderColor(borderColor)
      .setHideOnClickOutside(true)
      .setHideOnFrameResize(false)

    options.balloonCustomizer?.accept(balloonBuilder)

    if (manager.isNewUi) {
      balloonBuilder.setBorderInsets(JBUI.insets(9, 7, 11, 7)).setPointerSize(JBUI.size(16, 8)).setPointerShiftedToStart(
        true).setCornerRadius(JBUI.scale(8))
    }

    val balloon = balloonBuilder.createBalloon()
    if (balloon is BalloonImpl) {
      balloon.setHideOnClickOutside(false)
      NotificationsManagerImpl.frameActivateBalloonListener(balloon) {
        manager.coroutineScope.launch {
          delay(100.milliseconds)
          withContext(Dispatchers.EDT) {
            balloon.setHideOnClickOutside(true)
          }
        }
      }
    }

    listenerWrapper.balloon = balloon
    entry.balloon = balloon
    Disposer.register(balloon) {
      entry.toolWindow.isPlaceholderMode = false
      entry.balloon = null
    }
    Disposer.register(entry.disposable, balloon)
    return balloon
  }
}
