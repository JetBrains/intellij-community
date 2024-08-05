// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.apple.eawt.Application
import com.apple.eawt.FullScreenAdapter
import com.apple.eawt.FullScreenListener
import com.apple.eawt.FullScreenUtilities
import com.apple.eawt.event.FullScreenEvent
import com.intellij.ide.ActiveWindowsWatcher
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.executeOnCancelInEdt
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ToolbarService.Companion.getInstance
import com.intellij.ui.mac.MacFullScreenControlsManager.configureForEmptyToolbarHeader
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.sun.jna.Native
import com.sun.jna.platform.mac.CoreFoundation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class MacMainFrameDecorator(frame: IdeFrameImpl, glassPane: IdeGlassPane, coroutineScope: CoroutineScope) : IdeFrameDecorator(frame) {
  companion object {
    private val LOG: Logger
      get() = logger<MacMainFrameDecorator>()

    const val FULL_SCREEN: String = "Idea.Is.In.FullScreen.Mode.Now"

    const val FULL_SCREEN_PROGRESS: String = "Idea.Is.In.FullScreen.Mode.Progress"

    const val IGNORE_EXIT_FULL_SCREEN = "Idea.Ignore.Exit.FullScreen"
  }

  @Suppress("FunctionName")
  private interface MyCoreFoundation : CoreFoundation {
    fun CFPreferencesCopyAppValue(key: CoreFoundation.CFStringRef?, applicationID: CoreFoundation.CFStringRef?): CoreFoundation.CFStringRef?

    companion object {
      @JvmField
      val INSTANCE: MyCoreFoundation = Native.load("CoreFoundation", MyCoreFoundation::class.java)
    }
  }

  internal val dispatcher: EventDispatcher<FSListener> = EventDispatcher.create(FSListener::class.java)

  private val tabsHandler: MacWinTabsHandler =
    if (MacWinTabsHandler.isVersion2()) MacWinTabsHandlerV2(frame, coroutineScope) else MacWinTabsHandler(frame, coroutineScope)

  override var isInFullScreen: Boolean = false
    private set

  init {
    FullScreenUtilities.setWindowCanFullScreen(frame, true)

    // Native full-screen listener can be set only once
    FullScreenUtilities.addFullScreenListenerTo(frame, object : FullScreenListener {
      override fun windowEnteringFullScreen(event: FullScreenEvent) {
        dispatcher.getMulticaster().windowEnteringFullScreen(event)
      }

      override fun windowEnteredFullScreen(event: FullScreenEvent) {
        dispatcher.getMulticaster().windowEnteredFullScreen(event)
      }

      override fun windowExitingFullScreen(event: FullScreenEvent) {
        dispatcher.getMulticaster().windowExitingFullScreen(event)
      }

      override fun windowExitedFullScreen(event: FullScreenEvent) {
        dispatcher.getMulticaster().windowExitedFullScreen(event)
      }
    })
    dispatcher.addListener(object : FSAdapter() {
      override fun windowEnteringFullScreen(event: FullScreenEvent) {
        configureForEmptyToolbarHeader(true)
        frame.togglingFullScreenInProgress = true
        val rootPane = frame.rootPane
        if (rootPane != null && rootPane.border != null) {
          rootPane.setBorder(null)
        }
        tabsHandler.enteringFullScreen()
      }

      override fun windowEnteredFullScreen(event: FullScreenEvent) {
        frame.togglingFullScreenInProgress = false
        // We can get the notification when the frame has been disposed
        val rootPane = frame.rootPane
        rootPane?.putClientProperty(FULL_SCREEN, true)
        enterFullScreen()
        frame.validate()
        notifyFrameComponents(true)
      }

      override fun windowExitingFullScreen(e: FullScreenEvent) {
        frame.togglingFullScreenInProgress = true
      }

      override fun windowExitedFullScreen(event: FullScreenEvent) {
        configureForEmptyToolbarHeader(false)
        frame.togglingFullScreenInProgress = false
        // We can get the notification when the frame has been disposed
        val rootPane = frame.rootPane
        if (!ExperimentalUI.isNewUI() || !CustomWindowHeaderUtil.isToolbarInHeader(UISettings.getInstance(), false)) {
          getInstance().setCustomTitleBar(frame, rootPane) { runnable ->
            executeOnCancelInEdt(coroutineScope) { runnable.run() }
          }
        }
        exitFullScreen()
        ActiveWindowsWatcher.addActiveWindow(frame)
        frame.validate()
        notifyFrameComponents(false)
      }
    })

    if (!ExperimentalUI.isNewUI()) {
      glassPane.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount == 2 && e.y <= UIUtil.getTransparentTitleBarHeight(frame.rootPane)) {
            val appleActionOnDoubleClick = CoreFoundation.CFStringRef.createCFString("AppleActionOnDoubleClick")
            val apple_global_domain = CoreFoundation.CFStringRef.createCFString("Apple Global Domain")
            val res = MyCoreFoundation.INSTANCE.CFPreferencesCopyAppValue(
              appleActionOnDoubleClick,
              apple_global_domain)
            if (res != null && res.stringValue() != "Maximize") {
              if (frame.extendedState == Frame.ICONIFIED) {
                frame.setExtendedState(Frame.NORMAL)
              }
              else {
                frame.setExtendedState(Frame.ICONIFIED)
              }
            }
            else {
              if (frame.extendedState == Frame.MAXIMIZED_BOTH) {
                frame.setExtendedState(Frame.NORMAL)
              }
              else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH)
              }
            }
            apple_global_domain.release()
            appleActionOnDoubleClick.release()
            res?.release()
          }
          super.mouseClicked(e)
        }
      }, coroutineScope)
    }
  }

  private fun enterFullScreen() {
    isInFullScreen = true
    LOG.debug { "Full screen set flag true for $frame" }
    storeFullScreenStateIfNeeded()
    frame.rootPane?.putClientProperty(FULL_SCREEN_PROGRESS, null)
    tabsHandler.enterFullScreen()
  }

  private fun exitFullScreen() {
    val rootPane = frame.rootPane
    rootPane?.putClientProperty(FULL_SCREEN_PROGRESS, null)
    isInFullScreen = rootPane?.getClientProperty(IGNORE_EXIT_FULL_SCREEN) != null
    LOG.debug { "Full screen set flag false/$isInFullScreen for $frame" }
    storeFullScreenStateIfNeeded()
    rootPane?.putClientProperty(FULL_SCREEN, null)
    rootPane?.putClientProperty(IGNORE_EXIT_FULL_SCREEN, null)
    tabsHandler.exitFullScreen()
  }

  override fun setStoredFullScreen(state: Boolean) {
    isInFullScreen = state
    val rootPane = frame.rootPane
    if (rootPane != null) {
      rootPane.putClientProperty(FULL_SCREEN, state)
      if (rootPane.border != null) {
        rootPane.setBorder(null)
      }
    }
  }

  private fun storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    frame.doLayout()
  }

  override fun setProject() {
    tabsHandler.setProject()
  }

  override suspend fun toggleFullScreen(state: Boolean): Boolean {
    LOG.debug { "Full screen state $state requested for $frame" }
    // We delay the execution using 'invokeLater' to account for the case when a window might be made visible in the same EDT event.
    // macOS can auto-open that window in full-screen mode, but we won't find this out till the notification arrives.
    // That notification comes as a priority event, so such an 'invokeLater' is enough to fix the problem.
    // Note that subsequent invocations of current method in the same or close enough EDT events aren't supported well, but
    // such usage scenarios are not known at the moment.
    return withContext(RawSwingDispatcher) {
      if (isInFullScreen == state) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Full screen is already at state $state for $frame")
        }
        return@withContext state
      }
      else {
        suspendCoroutine { continuation ->
          val preEventReceived = AtomicBoolean()
          val listener = object : FSAdapter() {
            override fun windowEnteringFullScreen(e: FullScreenEvent) {
              LOG.debug { "entering full screen: $frame" }
              preEventReceived.set(true)
            }

            override fun windowExitingFullScreen(e: FullScreenEvent) {
              LOG.debug { "exiting full screen: $frame" }
              preEventReceived.set(true)
            }

            override fun windowExitedFullScreen(event: FullScreenEvent) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("exited full screen: $frame")
              }
              continuation.resume(false)
            }

            override fun windowEnteredFullScreen(event: FullScreenEvent) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("entered full screen: $frame")
              }
              notifyFrameComponents(true)
              continuation.resume(true)
            }
          }

          coroutineContext.job.invokeOnCompletion {
            dispatcher.removeListener(listener)
          }

          dispatcher.addListener(listener)
          LOG.debug { "Toggling full screen for $frame" }
          frame.rootPane?.putClientProperty(FULL_SCREEN_PROGRESS, true)
          Application.getApplication().requestToggleFullScreen(frame)
          Foundation.executeOnMainThread(false, false, Runnable {
            SwingUtilities.invokeLater(
              Runnable {
                // At this point, after a 'round-trip' to AppKit thread and back to EDT,
                // we know that `[NSWindow toggleFullScreen]` method has definitely started execution.
                // If it hasn't dispatched pre-transitioning event (windowWillEnterFullScreen/windowWillExitFullScreen),
                // we assume that the transitioning won't happen at all, and complete the promise.
                // One known case when `[NSWindow toggleFullScreen]` method does nothing
                // is when it's invoked for an 'inactive' tab in a 'tabbed' window group.
                if (preEventReceived.get()) {
                  LOG.debug { "pre-transitioning event received for: $frame" }
                }
                else {
                  LOG.debug { "pre-transitioning event not received for: $frame" }
                  continuation.resume(isInFullScreen)
                }
              })
          })
        }
      }
    }
  }

  override fun appClosing() {
    tabsHandler.appClosing()
  }

  override val isTabbedWindow: Boolean
    get() = MergeAllWindowsAction.isTabbedWindow(frame)

  interface FSListener : FullScreenListener, EventListener

  open class FSAdapter : FullScreenAdapter(), FSListener
}
