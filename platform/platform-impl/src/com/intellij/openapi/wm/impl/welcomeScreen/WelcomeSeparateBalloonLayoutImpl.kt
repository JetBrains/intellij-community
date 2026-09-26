// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.BalloonImpl
import com.intellij.ui.BalloonLayoutData
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollBar
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.math.max

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class WelcomeSeparateBalloonLayoutImpl(parent: JRootPane, insets: Insets) : WelcomeBalloonLayoutImpl(parent, insets) {
  private val myShowState = ShowState()
  private val myScrollController = ScrollController()

  override fun add(newBalloon: Balloon, layoutData: Any?) {
    if (layoutData is BalloonLayoutData && layoutData.welcomeScreen && newBalloon is BalloonImpl) {
      layoutData.doLayout = Runnable { layoutBalloons() }
      newBalloon.isBlockClicks = true
      newBalloon.setAnimationEnabled(false)
      newBalloon.content.putClientProperty(TYPE_KEY, layoutData.type)
      newBalloon.setHideListener(object : BalloonImpl.HideListenerWithMouse {
        override fun run(event: MouseEvent) {
          if (!myVisible) {
            return
          }
          val relativePoint = RelativePoint(event)
          if (myScrollController.isInside(relativePoint)) {
            return
          }
          for (balloon in balloons) {
            if (balloon !== newBalloon && (balloon as BalloonImpl).isInside(relativePoint)) {
              if (myScrollController.checkClip(balloon, relativePoint)) {
                break
              }
              return
            }
          }
          run()
        }

        override fun run() {
          hideListener?.run()
          if (myVisible) {
            updateVisible(false)
            myScrollController.hide(true)
            myShowState.hide()
          }
        }
      })
      Disposer.register(newBalloon) {
        balloons.remove(newBalloon)
        updateBalloons()
      }
      balloons.add(newBalloon)
      if (!newBalloon.isDisposed && layeredPane!!.isShowing) {
        newBalloon.show(layeredPane)
        newBalloon.component?.isVisible = myVisible
      }
      updateBalloons()
      ApplicationManager.getApplication().getMessageBus().syncPublisher(BALLOON_NOTIFICATION_TOPIC).newNotifications()
    }
    else {
      super.add(newBalloon, layoutData)
    }
  }

  fun autoPopup() {
    val balloonLayout = WelcomeFrame.getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl
    if (balloonLayout != null && !balloonLayout.myVisible && balloonLayout.locationComponent != null) {
      showPopup()
    }
  }

  override fun showPopup() {
    if (myShowState.isRecentlyHidden()) {
      hideListener?.run()
      return
    }
    updateVisible(true)
    layoutBalloons()
    myShowState.show()
  }

  private fun updateVisible(visible: Boolean) {
    myVisible = visible
    for (balloon in balloons) {
      (balloon as BalloonImpl).component.isVisible = visible
    }
  }

  override fun queueRelayout() {
    if (myVisible) {
      layoutBalloons()
    }
  }

  private fun layoutBalloons() {
    if (myLayoutBaseComponent == null || layeredPane == null || balloons.isEmpty()) {
      return
    }

    myScrollController.ensureStart(layeredPane!!)
    calculateSize()

    val startY = SwingUtilities.convertPoint(myLayoutBaseComponent, 0, 0, layeredPane).y
    val totalWidth = layeredPane!!.size.width
    val header = layeredPane!!.getClientProperty(FlatWelcomeFrame.CUSTOM_HEADER)
    val headerHeight = if (header is JComponent) { header.height } else 0

    myScrollController.configure(totalWidth, headerHeight, startY - headerHeight)
    setBounds(balloons, totalWidth - JBUI.scale(10), startY + myScrollController.startY)
    myScrollController.setClip(headerHeight, startY)
  }

  private fun getTotalHeight(): Int {
    var height = 0
    for (balloon in balloons) {
      height += getSize(balloon).height
    }
    return height
  }

  private fun updateBalloons() {
    val types = ArrayList<NotificationType>()
    for (balloon in balloons) {
      types.add((balloon as BalloonImpl).content.getClientProperty(TYPE_KEY) as NotificationType)
    }
    ApplicationManager.getApplication().getMessageBus().syncPublisher(BALLOON_NOTIFICATION_TOPIC).notificationsChanged(types)

    if (myVisible) {
      if (balloons.isEmpty()) {
        myVisible = false
        myScrollController.hide(false)
      }
      else {
        layoutBalloons()
      }
    }
  }

  private inner class ScrollController {
    private var myScrollBar = JBScrollBar()
    private var myState = ScrollInfo(0, 0, 0, false)
    private var myStartValue = 0
    private var myValue = 0
    var startY = 0

    private var myAwtListener: AWTEventListener? = null

    fun isInside(relativePoint: RelativePoint): Boolean {
      return myScrollBar.isVisible && myScrollBar.contains(relativePoint.getPoint(myScrollBar))
    }

    fun hide(save: Boolean) {
      if (save && myScrollBar.isVisible) {
        myState.value = myValue
        myState.save = true
      }
      else {
        myState.save = false
      }
      hide()
    }

    private fun hide() {
      if (myAwtListener != null) {
        Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtListener)
        myAwtListener = null
      }
      myScrollBar.isVisible = false
      startY = 0
      myStartValue = 0
      myValue = 0
    }

    fun ensureStart(parent: JComponent) {
      if (myScrollBar.parent != null) {
        return
      }

      parent.add(myScrollBar)

      myScrollBar.toggle(true)
      hide()

      myScrollBar.addAdjustmentListener {
        val value = myScrollBar.value
        if (myValue != value) {
          myValue = value
          startY = myStartValue - value
          layoutBalloons()
        }
      }
    }

    fun configure(totalWidth: Int, startY: Int, endY: Int) {
      val totalHeight = getTotalHeight()

      if (totalHeight <= endY) {
        hide(false)
      }
      else {
        if (myScrollBar.isVisible) {
          val delta = endY - myScrollBar.model.extent + myScrollBar.maximum - totalHeight
          if (delta != 0) {
            if (myValue > 0) {
              myValue = max(0, myValue - delta)
            }
            myStartValue = totalHeight - endY
            this.startY = myStartValue - myValue
          }
        }
        else {
          myStartValue = totalHeight - endY

          if (myState.save && myState.totalHeight == totalHeight && myState.extent == endY) {
            myValue = myState.value
            this.startY = myStartValue - myValue
          }
          else {
            myValue = myStartValue
            this.startY = 0
          }
        }

        myState.totalHeight = totalHeight
        myState.extent = endY
        myState.save = false

        myScrollBar.setValues(myValue, endY, 0, totalHeight)
        val scrollBarWidth = myScrollBar.preferredSize.width
        myScrollBar.setBounds(totalWidth - scrollBarWidth, startY, scrollBarWidth, endY)
        myScrollBar.isVisible = true

        if (myAwtListener == null) {
          myAwtListener = AWTEventListener { event ->
            if (isInside(RelativePoint(event as MouseWheelEvent))) {
              myScrollBar.handleMouseWheelEvent(event)
            }
          }
          Toolkit.getDefaultToolkit().addAWTEventListener(myAwtListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        }
      }

      clearClip()
    }

    fun setClip(startY: Int, endY: Int) {
      if (myScrollBar.isVisible) {
        for (balloon in balloons) {
          val balloonImpl = balloon as BalloonImpl
          val bounds = balloonImpl.component.bounds
          if (bounds.y > endY || bounds.maxY < startY) {
            balloonImpl.clipY = -1
            balloonImpl.component.isVisible = false
          }
          else if (bounds.maxY > endY) {
            val clipY = endY - bounds.y
            balloonImpl.clipY = clipY
            balloonImpl.component.isVisible = true
            balloonImpl.setActionButtonsVisible(clipY > JBUI.scale(26))
          }
          else if (bounds.y < startY) {
            val clipY = startY - bounds.y
            balloonImpl.clipY = clipY
            balloonImpl.setTopClip(true)
            balloonImpl.component.isVisible = true
            balloonImpl.setActionButtonsVisible(false)
          }
          else {
            balloonImpl.clipY = -1
            balloonImpl.component.isVisible = true
          }
        }
      }
      else {
        clearClip()
      }
    }

    private fun clearClip() {
      for (balloon in balloons) {
        val balloonImpl = balloon as BalloonImpl
        balloonImpl.clipY = -1
        balloonImpl.setTopClip(false)
        balloonImpl.component?.isVisible = true
      }
    }

    fun checkClip(balloon: BalloonImpl, relativePoint: RelativePoint): Boolean {
      val clip = balloon.clipY
      if (clip == -1) {
        return false
      }
      if (clip == 0) {
        return true
      }
      return relativePoint.getPoint(balloon.component).y > clip
    }
  }

  private class ShowState {
    private var hiddenLongEnough = true
    private var timeHiddenAt: Long = 0

    fun show() {
      hiddenLongEnough = true
      timeHiddenAt = 0
    }

    fun hide() {
      hiddenLongEnough = false
      timeHiddenAt = System.currentTimeMillis()
    }

    fun isRecentlyHidden(): Boolean {
      if (hiddenLongEnough) {
        return false
      }
      hiddenLongEnough = true
      return System.currentTimeMillis() - timeHiddenAt < 200
    }
  }

  private data class ScrollInfo(
    var totalHeight: Int,
    var extent: Int,
    var value: Int,
    var save: Boolean
  )
}