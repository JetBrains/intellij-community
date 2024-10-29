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
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HintHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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
    ThreadingAssertions.assertEventDispatchThread()
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
    val builder = if (ExperimentalUI.isNewUI()) {
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(balloonText, MessageType.WARNING.defaultIcon,
                                                                HintHint.Status.Warning.foreground, HintHint.Status.Warning.background,
                                                                null).setBorderColor(HintHint.Status.Warning.border).setShowCallout(
        true).setBorderInsets(JBUI.insets(9, 7, 11, 7)).setPointerSize(JBUI.size(16, 8)).setPointerShiftedToStart(true).setCornerRadius(
        JBUI.scale(8))
    }
    else {
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(balloonText, MessageType.WARNING, null).setBorderInsets(
        DUMB_BALLOON_INSETS).setShowCallout(false)
    }
    myBalloon = builder.createBalloon()
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
      val point = relativePoint.point
      if (ExperimentalUI.isNewUI()) {
        val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context)
        if (component is EditorComponentImpl) {
          point.translate(0, -component.editor.lineHeight)
          return RelativePoint(relativePoint.component, point)
        }
        return relativePoint
      }
      val size = balloon.preferredSize
      point.translate(size.width / 2, 0)
      //here are included hardcoded insets, icon width and small hardcoded delta to show before guessBestPopupLocation point
      point.translate(-DUMB_BALLOON_INSETS.left - AllIcons.General.BalloonWarning.iconWidth - scale(6), 0)
      return RelativePoint(relativePoint.component, point)
    }
  }
}
