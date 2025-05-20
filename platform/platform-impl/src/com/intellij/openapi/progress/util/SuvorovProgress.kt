// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.impl.getGlobalThreadingSupport
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.event.InvocationEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The IDE needs to run certain AWT events as soon as possible.
 * This class handles the situation where the IDE is frozen on acquisition of a lock, and instead of waiting for lock permit,
 * it dispatches certain safe events.
 *
 * It is relevant for the following scenario
 * ```kotlin
 * // bgt
 * writeAction { Thread.sleep(100000) } // some intensive work in background
 *
 * // edt
 * ReadAction.run {} // blocked until write lock is released
 * ```
 *
 * If the freeze lasts too long, the IDE will show a "modal" progress indicator and drop accumulated input events.
 *
 * The name of this class is an allusion to [PotemkinProgress] (which in turn was named after [Potemkin villages](https://en.wikipedia.org/wiki/Potemkin_village)).
 * However, the name of this class alludes to the occupation of both Grigory Potemkin and Alexander Suvorov.
 */
@ApiStatus.Internal
object SuvorovProgress {

  @JvmStatic
  fun dispatchEventsUntilComputationCompletes(awaitedValue: Deferred<*>) {
    val showingDelay = Registry.get("ide.suvorov.progress.showing.delay.ms").asInteger()
    val stealer = processInvocationEventsWithoutDialog(awaitedValue, showingDelay)

    if (awaitedValue.isCompleted) {
      stealer.dispatchAllExistingEvents()
      stealer.drainUndispatchedInputEvents().forEach {
        IdeEventQueue.getInstance().doPostEvent(it, true)
      }
      return
    }

    val value = if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
      "None"
    }
    else {
      Registry.get("ide.suvorov.progress.kind").selectedOption
    }
    when (value) {
      "None" -> processInvocationEventsWithoutDialog(awaitedValue, Int.MAX_VALUE)
      "Spinning" -> if (Registry.`is`("editor.allow.raw.access.on.edt")) {
        showSpinningProgress(awaitedValue)
      }
      else {
        thisLogger().warn("Spinning progress would not work without enabled registry value `editor.allow.raw.access.on.edt`")
        processInvocationEventsWithoutDialog(awaitedValue, Int.MAX_VALUE)
      }
      "Bar", "Overlay" -> showPotemkinProgress(awaitedValue, isBar = value == "Bar")
      else -> throw IllegalArgumentException("Unknown value for registry key `ide.freeze.fake.progress.kind`: $value")
    }
  }

  private fun showPotemkinProgress(awaitedValue: Deferred<*>, isBar: Boolean) {
    // some focus machinery may require Write-Intent read action
    // we need to remove it from there
    getGlobalThreadingSupport().relaxPreventiveLockingActions {
      val progress = if (isBar) {
        PotemkinProgress(CommonBundle.message("title.long.non.interactive.progress"), null, null, null)
      }
      else {
        val window = SwingUtilities.getRootPane(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
        PotemkinOverlayProgress(window, false)
      }.apply { setDelayInMillis(0) }
      progress.start()
      try {
        do {
          progress.interact()
          sleep() // avoid touching the progress too much
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
      }
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  private fun processInvocationEventsWithoutDialog(awaitedValue: Deferred<*>, showingDelay: Int): EventStealer {
    val disposable = Disposer.newDisposable()
    val stealer = EventStealer(disposable) {}
    try {
      if (awaitedValue.isCompleted) {
        return stealer
      }
      repostAllEvents()
      awaitedValue.invokeOnCompletion {
        // when the awaited value is ready, we need to signal the event dispatcher that it needs to stop waiting
        EventQueue.invokeLater(EventStealer.DispatchTerminationEvent.INSTANCE)
      }
      if (awaitedValue.isCompleted) {
        return stealer
      }
      stealer.dispatchInvocationEventsForDuration(showingDelay.toLong())
    }
    finally {
      Disposer.dispose(disposable)
    }
    return stealer
  }

  /**
   * Protection against race condition: imagine someone posted locking event, and then MarkedRunnable.
   * locking event will not intercept MarkedRunnable because event stealer is not installed
   */
  private fun repostAllEvents() {
    val queue = IdeEventQueue.getInstance()
    val events = generateSequence {
      if (queue.peekEvent() != null) {
        IdeEventQueue.getInstance().nextEvent
      }
      else {
        null
      }
    }.toList()
    events.forEach { event -> queue.doPostEvent(event, true) }
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
      repostAllEvents()

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
    Thread.sleep(0, 100_000)
  }

  abstract class ForcedWriteActionRunnable : Runnable {

    companion object {
      private const val NAME = "ForcedWriteActionRunnable"

      fun isMarkedRunnable(event: InvocationEvent): Boolean {
        return event.toString().contains(NAME)
      }
    }

    override fun toString(): String {
      return NAME
    }
  }
}
