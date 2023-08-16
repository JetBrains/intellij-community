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
import java.awt.Insets
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * @author Alexander Lobas
 */
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
            myScrollController.hide()
            myShowState.hide()
          }
        }
      })
      Disposer.register(newBalloon) {
        balloons.remove(newBalloon)
        updateBalloons()
      }
      balloons.add(newBalloon)
      if (!newBalloon.isDisposed) {
        newBalloon.show(layeredPane)
        newBalloon.component.isVisible = myVisible
      }
      updateBalloons()
    }
    else {
      super.add(newBalloon, layoutData)
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

    myScrollController.configure(totalWidth, startY)
    setBounds(balloons, totalWidth - JBUI.scale(10), startY + myScrollController.startY)
    myScrollController.setClip(startY)
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
        myScrollController.hide()
      }
      else {
        layoutBalloons()
      }
    }
  }

  private inner class ScrollController {
    private var myScrollBar = JBScrollBar()
    private var myStartValue = 0
    private var myValue = 0
    var startY = 0

    fun isInside(relativePoint: RelativePoint): Boolean {
      return myScrollBar.isVisible && myScrollBar.contains(relativePoint.getPoint(myScrollBar))
    }

    fun hide() {
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

    fun configure(totalWidth: Int, startY: Int) {
      val totalHeight = getTotalHeight()

      if (totalHeight <= startY) {
        hide()
      }
      else {
        if (myScrollBar.isVisible) {
          val delta = myScrollBar.model.extent - startY
          if (delta != 0) {
            myValue += delta
            myStartValue += delta
            this.startY += delta // XXX
          }
        }
        else {
          this.startY = 0
          myValue = totalHeight - startY
          myStartValue = myValue
        }

        myScrollBar.setValues(myValue, startY, 0, totalHeight)
        val scrollBarWidth = myScrollBar.preferredSize.width
        myScrollBar.setBounds(totalWidth - scrollBarWidth, 0, scrollBarWidth, startY)
        myScrollBar.isVisible = true
      }
    }

    fun setClip(startY: Int) {
      if (myScrollBar.isVisible) {
        for (balloon in balloons) {
          val balloonImpl = balloon as BalloonImpl
          val bounds = balloonImpl.component.bounds
          if (bounds.y > startY) {
            balloonImpl.clipY = 0
          }
          else if (bounds.maxY > startY) {
            balloonImpl.clipY = startY - bounds.y
          }
          else {
            balloonImpl.clipY = -1
          }
        }
      }
      else {
        for (balloon in balloons) {
          (balloon as BalloonImpl).clipY = -1
        }
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
}