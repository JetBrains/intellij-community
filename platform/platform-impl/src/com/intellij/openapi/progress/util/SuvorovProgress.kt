// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadWriteActionSupport
import com.intellij.openapi.application.impl.InternalThreading
import com.intellij.openapi.application.rw.PlatformReadWriteActionSupport
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ui.NiceOverlayUi
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.application
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.ui.EDT
import com.intellij.util.ui.GraphicsUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

/**
 * The IDE needs to run certain AWT events as soon as possible.
 * This class handles the situation where the IDE is frozen on acquisition of a lock, and instead of waiting for lock permit,
 * it dispatches certain events.
 *
 * The types of events are described here: [com.intellij.openapi.progress.util.EventStealer.isUrgentInvocationEvent].
 * One of these events is [com.intellij.openapi.application.ThreadingSupport.RunnableWithTransferredWriteAction],
 * which is used to perform `invokeAndWait` from inside a background write action (see [com.intellij.util.concurrency.TransferredWriteActionService.runOnEdtWithTransferredWriteActionAndWait])
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

  private val awtComponentLock = (object : Component() {}).treeLock

  @Volatile
  private lateinit var eternalStealer: EternalEventStealer

  // exposed in a field for debugging
  @Volatile
  private var stealer: EventStealer? = null

  fun init(disposable: Disposable) {
    eternalStealer = EternalEventStealer(disposable)
  }

  private val title: AtomicReference<@Nls String?> = AtomicReference()

  fun <T> withProgressTitle(title: String, action: () -> T): T {
    val oldTitle = this.title.getAndSet(title)
    try {
      return action()
    }
    finally {
      this.title.set(oldTitle)
    }
  }

  /**
   * We have a major conceptual problem -- many rendering computations are executing under [Component.treeLock],
   * where they access PSI and consequently they acquire read action.
   * At the same time, some computations inside read actions initialize Swing components which internally also acquire [Component.treeLock]
   *
   * This results in a deadlock caused by the incorrect order of locks.
   * This is particularly actual with the background write action which can stall of read actions.
   *
   * Too many clients already rely on this behavior, so we solve the problem for this particular pair of locks:
   * when we detect such situation, we forcefully retry the pending background write action -- after release of pending WA,
   * some read actions can progress, including the one on EDT.
   *
   * See IJPL-211485
   */
  fun tryProgressWithPendingBackgroundWriteAction() {
    if (Thread.holdsLock(awtComponentLock)) {
      val application = ApplicationManager.getApplication()
      val rwService = application.serviceIfCreated<ReadWriteActionSupport>()
      if (rwService is PlatformReadWriteActionSupport) {
        rwService.signalWriteActionNeedsToBeRetried()
      }
    }
  }

  @JvmStatic
  fun dispatchEventsUntilComputationCompletes(awaitedValue: Deferred<*>) {
    val showingDelay = Registry.get("ide.suvorov.progress.showing.delay.ms").asInteger()

    tryProgressWithPendingBackgroundWriteAction()

    processInvocationEventsWithoutDialog(awaitedValue, showingDelay)

    if (awaitedValue.isCompleted) {
      return
    }

    LifecycleUsageTriggerCollector.onFreezePopupShown()

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
      "NiceOverlay" -> {
        val currentFocusedPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow?.let(SwingUtilities::getRootPane)
        // IJPL-203107 in remote development, there is no graphics for a component
        if (currentFocusedPane == null || GraphicsUtil.safelyGetGraphics(currentFocusedPane) == null) {
          // can happen also in tests
          processInvocationEventsWithoutDialog(awaitedValue, Int.MAX_VALUE)
        }
        else if (title.get() != null) {
          showPotemkinProgress(awaitedValue, true)
        } else {
          showNiceOverlay(awaitedValue, currentFocusedPane)
        }
      }
      "Bar", "Overlay" -> showPotemkinProgress(awaitedValue, isBar = value == "Bar")
      else -> throw IllegalArgumentException("Unknown value for registry key `ide.freeze.fake.progress.kind`: $value")
    }
  }

  private fun showNiceOverlay(awaitedValue: Deferred<*>, rootPane: JRootPane) {
    val niceOverlay = NiceOverlayUi(rootPane, false)

    val disposable = Disposer.newDisposable()
    val stealer = EventStealer(disposable, true) { event ->
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
          val dumpFile = PerformanceWatcher.getInstance().dumpThreads("freeze-popup", true, false)
          if (dumpFile != null) {
            if (Files.exists(dumpFile)) {
              RevealFileAction.openFile(dumpFile)
            }
            else {
              logger<SuvorovProgress>().error("Failed to dump threads to $dumpFile")
            }
          }
        })
      }
    }
    this.stealer = stealer

    repostAllEvents()
    var oldTimestamp = System.currentTimeMillis()
    try {
      while (!awaitedValue.isCompleted) {
        val newTimestamp = System.currentTimeMillis()
        if (newTimestamp - oldTimestamp >= 10) {
          // we do not want to redraw the UI too frequently
          oldTimestamp = newTimestamp
          niceOverlay.redrawMainComponent()
        }
        stealer.dispatchEvents(0)
        stealer.waitForPing(10)
      }
    }
    finally {
      this.stealer = null
      niceOverlay.close()
      Disposer.dispose(disposable)
    }
  }

  fun logErrorIfTooLong(): AutoCloseable {
    val loggingJob = deadlockLoggingJob(stealer, eternalStealer)
    return AutoCloseable { loggingJob.cancel() }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun deadlockLoggingJob(stealer: EventStealer?, eternalStealer: EternalEventStealer): Job = GlobalScope.launch(blockingDispatcher) {
    delay(5.seconds)
    val stealerInfo = stealer?.dumpDebugInfo() ?: "No EventStealer"
    val eternalStealerInfo = eternalStealer.dumpDebugInfo()
    val ideEventQueueInfo = with (IdeEventQueue.getInstance()) {
      buildString {
        appendLine("IdeEventQueueInfo")
        appendLine("- trueCurrentEvent = $trueCurrentEvent")
        appendLine("- dispatchers = ${IdeEventQueue::class.java.getDeclaredField("dispatchers").also { it.setAccessible(true) }.get(this@with)}")
        appendLine("- nonLockingDispatchers = ${IdeEventQueue::class.java.getDeclaredField("nonLockingDispatchers").also { it.setAccessible(true) }.get(this@with)}")
        appendLine("- postEventListeners = ${IdeEventQueue::class.java.getDeclaredField("postEventListeners").also { it.setAccessible(true) }.get(this@with)}")
        val queues = EventQueue::class.java.getDeclaredField("queues").also { it.setAccessible(true) }.get(this@with) as Array<*>
        queues.forEachIndexed { index, queue ->
          var head = queue?.javaClass?.getDeclaredField("head")?.also { it.setAccessible(true) }?.get(queue)
          val tail = queue?.javaClass?.getDeclaredField("tail")?.also { it.setAccessible(true) }?.get(queue)
          append("- queue#$index ($queue):")
          if (head == null) {
            appendLine()
            return@forEachIndexed
          }
          val eventField = head.javaClass.getDeclaredField("event").also { it.setAccessible(true) }
          val nextField = head.javaClass.getDeclaredField("next").also { it.setAccessible(true) }
          while (head != null && head != tail) {
            append(eventField.get(head))
            append(", ")
            head = nextField.get(head)
          }
          appendLine()
        }
      }
    }

    val exception = Throwable("Probable deadlock detected in SuvorovProgress:\n$stealerInfo\n$eternalStealerInfo\n$ideEventQueueInfo")
    exception.stackTrace = EDT.getEventDispatchThread().stackTrace
    logger<SuvorovProgress>().error(exception)
  }

  private fun showPotemkinProgress(awaitedValue: Deferred<*>, isBar: Boolean) {
    @Suppress("HardCodedStringLiteral") val title = this.title.get()
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
        if (progress is PotemkinProgress) {
          progress.dispatchAllInvocationEvents()
        }
        else if (progress is PotemkinOverlayProgress) {
          progress.dispatchAllInvocationEvents()
        }
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

  @OptIn(InternalCoroutinesApi::class)
  private fun processInvocationEventsWithoutDialog(awaitedValue: Deferred<*>, showingDelay: Int) {
    eternalStealer.dispatchAllEventsForTimeout(showingDelay.toLong(), awaitedValue)
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
  private var counter = 0
  private val specialEvents = LinkedBlockingQueue<ForcedEvent>()

  init {
    IdeEventQueue.getInstance().addPostEventListener(
      { event ->
        if (event is InternalThreading.TransferredWriteActionEvent) {
          specialEvents.add(TransferredWriteActionWrapper(event))
        }
        false
      }, disposable)
  }

  fun dumpDebugInfo(): String {
    val events = specialEvents.map { event -> event.toString() }
    return "EternalEventStealer: ${specialEvents.size} events " + if (events.isEmpty()) "" else "($events)"
  }

  fun dispatchAllEventsForTimeout(timeoutMillis: Long, deferred: Deferred<*>) {
    val initialMark = System.nanoTime()

    val id = counter++
    deferred.invokeOnCompletion {
      specialEvents.add(TerminalEvent(id))
    }

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
        when (val event = specialEvents.poll() ?: specialEvents.poll(toSleep, TimeUnit.MILLISECONDS)) {
          is TerminalEvent -> {
            // return only if we get the event for the right id
            if (event.id == id) {
              return
            }
          }
          is TransferredWriteActionWrapper -> event.event.execute()
          null -> Unit
        }
      } catch (_ : InterruptedException) {
        // we still return locking result regardless of interruption
        Thread.currentThread().interrupt()
      }
    }
  }
}

private sealed interface ForcedEvent

private class TerminalEvent(val id: Int) : ForcedEvent

@JvmInline
private value class TransferredWriteActionWrapper(val event: InternalThreading.TransferredWriteActionEvent) : ForcedEvent

/**
 * Protection against race condition: imagine someone posted an event that wants lock, and then they post [TransferredWriteActionWrapper].
 * The second event will go into the main event queue because event stealer was not installed, but we need to execute it very quickly.
 */
private fun repostAllEvents() {
  val queue = IdeEventQueue.getInstance()
  val events = ArrayList<AWTEvent>()
  val topEvent = IdeEventQueue.getInstance().trueCurrentEvent
  if (EventStealer.isUrgentInvocationEvent(topEvent)) {
    events.add(IdeEventQueue.getInstance().trueCurrentEvent)
  }
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