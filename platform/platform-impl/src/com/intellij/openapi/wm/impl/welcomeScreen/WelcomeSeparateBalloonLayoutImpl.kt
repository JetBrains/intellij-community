// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.BalloonImpl
import com.intellij.ui.BalloonLayoutData
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Insets
import java.awt.event.MouseEvent
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * @author Alexander Lobas
 */
class WelcomeSeparateBalloonLayoutImpl(parent: JRootPane, insets: Insets) : WelcomeBalloonLayoutImpl(parent, insets) {
  private val myShowState = ShowState()

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
          for (balloon in balloons) {
            if (balloon !== newBalloon && (balloon as BalloonImpl).isInside(RelativePoint(event))) {
              return
            }
          }
          run()
        }

        override fun run() {
          hideListener?.run()
          if (myVisible) {
            updateVisible(false)
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

    calculateSize()

    val startX = layeredPane!!.size.width - JBUI.scale(10)
    val startY = SwingUtilities.convertPoint(myLayoutBaseComponent, 0, 0, layeredPane).y
    setBounds(balloons, startX, startY)
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
      }
      else {
        layoutBalloons()
      }
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