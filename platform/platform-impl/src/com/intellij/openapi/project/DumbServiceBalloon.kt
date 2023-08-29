// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.DumbModeBalloonCancelled
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.DumbModeBalloonProceededToActions
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.DumbModeBalloonRequested
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.DumbModeBalloonShown
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.DumbModeBalloonWasNotNeeded
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.TimeoutUtil
import com.intellij.util.ui.JBInsets

class DumbServiceBalloon(private val myProject: Project,
                         private val myService: Service) {
  private var myBalloon: Balloon? = null //used from EDT only

  // a workaround, it should not depend on DumbServiceImpl/DumbService and be a part of DumbServiceImpl
  interface Service {
    val isDumb: Boolean
    fun runWhenSmart(runnable: Runnable)
  }

  fun dispose() {
    if (myBalloon != null) {
      Disposer.dispose(myBalloon!!)
    }
  }

  fun showDumbModeActionBalloon(balloonText: @NlsContexts.PopupContent String,
                                runWhenSmartAndBalloonStillShowing: Runnable,
                                runWhenCancelled: Runnable) {
    if (LightEdit.owns(myProject)) return
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (!myService.isDumb) {
      DumbModeBalloonWasNotNeeded.log(myProject)
      runWhenSmartAndBalloonStillShowing.run()
      return
    }
    if (myBalloon != null) {
      //here should be an assertion that it does not happen, but now we have two dispatches of one InputEvent, see IDEA-227444
      return
    }
    tryShowBalloonTillSmartMode(balloonText, runWhenSmartAndBalloonStillShowing, runWhenCancelled)
  }

  private fun tryShowBalloonTillSmartMode(balloonText: @NlsContexts.PopupContent String,
                                          runWhenSmartAndBalloonNotHidden: Runnable,
                                          runWhenCancelled: Runnable) {
    LOG.assertTrue(myBalloon == null)
    val startTimestamp = System.nanoTime()
    DumbModeBalloonRequested.log(myProject)
    myBalloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(balloonText, MessageType.WARNING, null).setBorderInsets(
      DUMB_BALLOON_INSETS).setShowCallout(false).createBalloon()
    myBalloon!!.setAnimationEnabled(false)
    myBalloon!!.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (myBalloon == null) {
          return
        }
        DumbModeBalloonCancelled.log(myProject)
        runWhenCancelled.run()
        myBalloon = null
      }
    })
    myService.runWhenSmart {
      val balloon: Balloon = myBalloon ?: return@runWhenSmart
      DumbModeBalloonProceededToActions.log(myProject, TimeoutUtil.getDurationMillis(startTimestamp))
      runWhenSmartAndBalloonNotHidden.run()
      myBalloon = null
      balloon.hide()
    }
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context: DataContext ->
      if (!myService.isDumb) {
        return@onSuccess
      }
      if (myBalloon == null) {
        return@onSuccess
      }
      DumbModeBalloonShown.log(myProject)
      myBalloon!!.show(getDumbBalloonPopupPoint(myBalloon!!, context), Balloon.Position.above)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(DumbServiceBalloon::class.java)
    private val DUMB_BALLOON_INSETS = JBInsets.create(5, 8)
    private fun getDumbBalloonPopupPoint(balloon: Balloon, context: DataContext): RelativePoint {
      val relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(context)
      val size = balloon.preferredSize
      val point = relativePoint.point
      point.translate(size.width / 2, 0)
      //here are included hardcoded insets, icon width and small hardcoded delta to show before guessBestPopupLocation point
      point.translate(-DUMB_BALLOON_INSETS.left - AllIcons.General.BalloonWarning.iconWidth - scale(6), 0)
      return RelativePoint(relativePoint.component, point)
    }
  }
}
