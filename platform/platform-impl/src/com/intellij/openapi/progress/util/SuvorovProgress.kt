// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.impl.fus.FreezeUiUsageCollector
import com.intellij.openapi.progress.util.ui.NiceOverlayUi
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.application
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.util.concurrent.atomic.AtomicReference
import java.awt.event.InvocationEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.LinkedBlockingQueue
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
 * It is named after Alexander Suvorov, who shared the same occupation as Grigory Potemkin
 */
@ApiStatus.Internal
object SuvorovProgress {

  @Volatile
  private lateinit var eternalStealer: EternalEventStealer

  fun init(disposable: Disposable) {
    eternalStealer = EternalEventStealer(disposable)
  }

  private val title: AtomicReference<@Nls String> = AtomicReference()

  fun <T> withProgressTitle(title: String, action: () -> T): T {
    val oldTitle = this.title.getAndSet(title)
    try {
      return action()
    }
    finally {
      this.title.set(oldTitle)
    }
  }

  @JvmStatic
  fun dispatchEventsUntilComputationCompletes(awaitedValue: Deferred<*>) {
    val showingDelay = Registry.get("ide.suvorov.progress.showing.delay.ms").asInteger()
    processInvocationEventsWithoutDialog(awaitedValue, showingDelay)

    if (awaitedValue.isCompleted) {
      return
    }

    FreezeUiUsageCollector.reportUiFreezePopupVisible()

    // in tests, there is no UI scale, but we still want to run SuvorovProgress
    val isScaleInitialized = (application.isUnitTestMode || JBUIScale.isInitialized())

    val value = if (!LoadingState.COMPONENTS_LOADED.isOccurred || !isScaleInitialized) {
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
      "NiceOverlay" -> {
        val currentFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (currentFocusOwner == null) {
          // can happen also in tests
          processInvocationEventsWithoutDialog(awaitedValue, Int.MAX_VALUE)
        } else {
          showNiceOverlay(awaitedValue, currentFocusOwner)
        }
      }
      "Bar", "Overlay" -> showPotemkinProgress(awaitedValue, isBar = value == "Bar")
      else -> throw IllegalArgumentException("Unknown value for registry key `ide.freeze.fake.progress.kind`: $value")
    }
  }

  private fun showNiceOverlay(awaitedValue: Deferred<*>, currentFocusOwner: Component) {
    val niceOverlay = NiceOverlayUi(currentFocusOwner)

    val disposable = Disposer.newDisposable()
    val stealer = PotemkinProgress.startStealingInputEvents(
      { event ->
        var dumpThreads = false
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_CLICKED) {
          event.consume()
          val reaction = niceOverlay.mouseClicked(event.point)
          when (reaction) {
            NiceOverlayUi.ClickOutcome.DUMP_THREADS -> dumpThreads = true
            NiceOverlayUi.ClickOutcome.CLOSED, NiceOverlayUi.ClickOutcome.NOTHING -> Unit
          }
        }
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_MOVED) {
          event.consume()
          niceOverlay.mouseMoved(event.point)
        }
        if (event is KeyEvent && niceOverlay.dumpThreadsButtonShortcut == KeyStrokeAdapter.getDefaultKeyStroke(event)?.let { KeyboardShortcut(it, null) }) {
          event.consume()
          dumpThreads = true
        }
        if (dumpThreads) {
          ApplicationManager.getApplication().executeOnPooledThread(Runnable {
            val dumpDir = PerformanceWatcher.getInstance().dumpThreads("freeze-popup", true, false)
            if (dumpDir != null) {
              RevealFileAction.openFile(dumpDir)
            }
          })
        }
      }, disposable)

    try {
      while (!awaitedValue.isCompleted) {
        niceOverlay.redrawMainComponent()
        stealer.dispatchEvents(0)
        Thread.sleep(10)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun showPotemkinProgress(awaitedValue: Deferred<*>, isBar: Boolean) {
    // some focus machinery may require Write-Intent read action
    // we need to remove it from there
    getGlobalThreadingSupport().relaxPreventiveLockingActions {
      val title = this.title.get()
      val progress = if (title != null || isBar) {
        PotemkinProgress(title ?: CommonBundle.message("title.long.non.interactive.progress"), null, null, null)
      }
      else {
        val window = SwingUtilities.getRootPane(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
        PotemkinOverlayProgress(window, false)
      }.apply {
        setDelayInMillis(0)
        repostAllEvents()
      }
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
          progress.dialog.getPopup()?.setShouldUseWriteIntentReadAction(false)
          progress.progressFinished()
          progress.processFinish()
          Disposer.dispose(progress)
        }
        progress.stop()
      }
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  private fun processInvocationEventsWithoutDialog(awaitedValue: Deferred<*>, showingDelay: Int) {
    eternalStealer.enable()
    try {
      eternalStealer.dispatchAllEventsForTimeout(showingDelay.toLong(), awaitedValue)
    }
    finally {
      eternalStealer.disable()
    }
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
}

/**
 * High-performance interceptor of AWT events
 *
 * We instantiate this stealer and register it as postEventHook once to avoid complex interaction with Disposer on each entry to SuvorovProgress.
 * The goal of this class is to process very urgent AWT events despite the IDE being frozen.
 * One needs to be careful to maintain keep AWT events in the order they were posted.
 */
private class EternalEventStealer(disposable: Disposable) {
  @Volatile
  private var enabled = false

  private val specialEvents = LinkedBlockingQueue<SpecialDispatchEvent>()

  init {
    IdeEventQueue.getInstance().addPostEventListener(
      { event ->
        if (enabled && event.toString().contains(",runnable=${ThreadingSupport.RunnableWithTransferredWriteAction.NAME}")) {
          val specialDispatchEvent = SpecialDispatchEvent(event)
          specialEvents.add(specialDispatchEvent)
          IdeEventQueue.getInstance().doPostEvent(specialDispatchEvent, true)
          return@addPostEventListener true
        }
        false
      }, disposable)
  }

  fun enable() {
    enabled = true
  }

  fun dispatchAllEventsForTimeout(timeoutMillis: Long, deferred: Deferred<*>) {
    val initialMark = System.nanoTime()

    deferred.invokeOnCompletion {
      synchronized(this@EternalEventStealer) {
        (this@EternalEventStealer as Object).notifyAll()
      }
    }

    synchronized(this) {
      while (true) {
        val currentMark = System.nanoTime()
        val elapsedSinceStartNanos = currentMark - initialMark
        val toSleep = timeoutMillis - (elapsedSinceStartNanos / 1_000_000)
        if (toSleep <= 0) {
          return
        }
        if (deferred.isCompleted) {
          return
        }
        try {
          (this as Object).wait(toSleep)
        } catch (_ : InterruptedException) {
          // we still return locking result regardless of interruption
          Thread.currentThread().interrupt()
        }
        while (true) {
          val event = specialEvents.poll() ?: break
          event.execute()
        }
        if (!deferred.isActive) {
          return
        }
      }
    }
  }

  fun disable() {
    enabled = false
  }
}

private class SpecialDispatchEvent private constructor(val reference: AtomicReference<AWTEvent>) : InvocationEvent(Any(), {
  execute(reference)
}) {
  companion object {
    fun execute(ref: AtomicReference<AWTEvent>) {
      val actualEvent = ref.getAndSet(null) ?: return
      IdeEventQueue.getInstance().dispatchEvent(actualEvent)
    }
  }

  constructor(event: AWTEvent) : this(AtomicReference(event))

  fun execute() {
    execute(reference)
  }

  override fun toString(): String {
    return "SpecialDispatchEvent"
  }
}


/**
 * Protection against race condition: imagine someone posted an event that wants lock, and then they post [SuvorovProgress.ForcedWriteActionRunnable].
 * The second event will go into the main event queue because event stealer was not installed, but we need to execute it very quickly.
 * todo: we might get some performance if we catch [SuvorovProgress.ForcedWriteActionRunnable] in [EternalEventStealer]
 *  and execute it when [EternalEventStealer] starts dispatching events.
 *  This way we would avoid iterating over all stored events
 */
private fun repostAllEvents() {
  val queue = IdeEventQueue.getInstance()
  val events = ArrayList<AWTEvent>()
  while (true) {
    queue.peekEvent() ?: break
    val actualEvent = queue.nextEvent
    events.add(actualEvent)
  }
  var i = 0
  while (i < events.size) {
    queue.doPostEvent(events[i++], true)
  }
}