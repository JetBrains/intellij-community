// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.impl.getGlobalThreadingSupport
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * [PotemkinProgress] done right.
 *
 * Imagine the following scenario:
 * ```kotlin
 * // bgt
 * writeAction { Thread.sleep(100000) } // some intensive work in background
 *
 * // edt
 * ReadAction.run {} // blocked until write lock is released
 * ```
 *
 * In this situation, we have a freeze, because EDT is blocked on a lock in a single event.
 * Instead of blocking, we can show a "modal" progress and provide an impression that IDE is not dead.
 *
 * This progress starts when EDT is going to be blocked on the RWI lock, and finished when the required lock gets acquired.
 */
@ApiStatus.Internal
object SuvorovProgress {

  @JvmStatic
  fun dispatchEventsUntilConditionCompletes(awaitedValue: Deferred<*>) {
    val value = if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
      "None"
    }
    else {
      Registry.get("ide.suvorov.progress.kind").selectedOption
    }
    when (value) {
      "None" -> awaitedValue.asCompletableFuture().join()
      "Spinning" -> if (Registry.`is`("editor.allow.raw.access.on.edt")) {
        showSpinningProgress(awaitedValue)
      }
      else {
        thisLogger().warn("Spinning progress would not work without enabled registry value `editor.allow.raw.access.on.edt`")
        showPotemkinProgress(awaitedValue, true)
      }
      "Bar", "Overlay" -> showPotemkinProgress(awaitedValue, value == "Bar")
      else -> throw IllegalArgumentException("Unknown value for registry key `ide.freeze.fake.progress.kind`: $value")
    }
  }

  private fun showPotemkinProgress(awaitedValue: Deferred<*>, isBar: Boolean) {
    // some focus machinery may require Write-Intent read action
    // we need to remove it from there
    getGlobalThreadingSupport().relaxPreventiveLockingActions {
      val showingDelay = Registry.get("ide.suvorov.progress.showing.delay.ms").asInteger()
      val progress = getPotemkinProgress(showingDelay, isBar)
      progress.start()
      try {
        try {
          awaitedValue.asCompletableFuture().get(showingDelay.toLong(), TimeUnit.MILLISECONDS)
          return@relaxPreventiveLockingActions
        }
        catch (_: TimeoutException) {
          // ignore, now we need to paint the progress
        }
        do {
          progress.interact()
          sleep(); // avoid touching the progress too much
        }
        while (!awaitedValue.isCompleted)
      }
      finally {
        // we cannot acquire WI on closing
        if (progress is PotemkinProgress) {
          progress.dialog.getPopup()?.setShouldDisposeInWriteIntentReadAction(false)
          progress.progressFinished()
          progress.processFinish()
          Disposer.dispose(progress)
        }
        progress.stop()
        val pendingEvents = getPendingInputEvents(progress)
        pendingEvents.forEach {
          IdeEventQueue.getInstance().doPostEvent(it, true)
        }
      }
    }
  }

  fun getPendingInputEvents(progress: ProgressIndicator): List<InputEvent> {
    return when (progress) {
      is PotemkinProgress -> progress.drainUndispatchedInputEvents()
      is PotemkinOverlayProgress -> progress.drainUndispatchedInputEvents()
      else -> error("Unexpected progress type: $progress")
    }
  }

  fun ProgressIndicator.interact() {
    if (this is PotemkinProgress) {
      interact()
    }
    else if (this is PotemkinOverlayProgress) {
      interact()
    }
  }

  private fun getPotemkinProgress(showingDelay: Int, isBar: Boolean): ProgressIndicator {
    return if (isBar) {
      PotemkinProgress(CommonBundle.message("title.long.non.interactive.progress"), null, null, null).apply { setDelayInMillis(showingDelay) }
    }
    else {
      val window = SwingUtilities.getRootPane(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
      PotemkinOverlayProgress(window)
    }.apply { setDelayInMillis(showingDelay) }
  }

  private fun showSpinningProgress(awaitedValue: Deferred<*>) {
    getGlobalThreadingSupport().relaxPreventiveLockingActions {

      val icon = AsyncProcessIcon.createBig("Suvorov progress")
      val window = SwingUtilities.getRootPane(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)

      if (window == null) {
        awaitedValue.asCompletableFuture().join()
        return@relaxPreventiveLockingActions
      }

      icon.size = icon.preferredSize
      icon.isVisible = true

      val disposer = Disposer.newDisposable()
      val stealer = PotemkinProgress.startStealingInputEvents({ event ->
                                                                val source = event.source
                                                                // we want to permit resizing and moving the IDE window
                                                                if (source is JFrame) {
                                                                  source.dispatchEvent(event)
                                                                }
                                                              }, disposer)

      val host = window.layeredPane
      host.add(icon)
      // Swing tries its best to not draw anything that may not be on screen.
      // We need to trick it to mandatory drawing, and for this reason we make the host components opaque.
      val oldHostVisibile = host.isVisible
      val oldHostOpaque = host.isOpaque
      host.isVisible = true
      host.isOpaque = true

      icon.updateUI()
      icon.setBounds((window.width - icon.width) / 2, (window.height - icon.height) / 2, icon.width, icon.height)
      icon.resume()

      try {
        do {
          icon.validate()
          icon.tickAnimation()

          stealer.dispatchEvents(0)
          sleep() // avoid touching the progress too much
        }
        while (!awaitedValue.isCompleted)
      }
      finally {
        icon.suspend()
        host.isVisible = oldHostVisibile
        host.isOpaque = oldHostOpaque
        icon.isVisible = false
        Disposer.dispose(disposer)
        host.remove(icon)
      }
    }
  }

  private fun sleep() {
    Thread.sleep(5)
  }
}
