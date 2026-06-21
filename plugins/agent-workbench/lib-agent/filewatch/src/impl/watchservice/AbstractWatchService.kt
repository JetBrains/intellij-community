// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice

import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal abstract class AbstractWatchService : WatchService {
  private val queue = LinkedBlockingQueue<WatchKey>()
  private val poison = AbstractWatchKey(this, null, emptySet(), 1)
  private val open = AtomicBoolean(true)

  abstract fun register(watchable: WatchablePath, eventTypes: Iterable<WatchEvent.Kind<*>>): AbstractWatchKey

  val isOpen: Boolean
    get() = open.get()

  internal fun enqueue(key: AbstractWatchKey) {
    if (isOpen) {
      queue.add(key)
    }
  }

  open fun cancelled(key: AbstractWatchKey) {
  }

  override fun poll(): WatchKey? {
    checkOpen()
    return check(queue.poll())
  }

  override fun poll(timeout: Long, unit: TimeUnit): WatchKey? {
    checkOpen()
    return check(queue.poll(timeout, unit))
  }

  override fun take(): WatchKey {
    checkOpen()
    return check(queue.take()) ?: error("queue.take() returned null")
  }

  private fun check(key: WatchKey?): WatchKey? {
    if (key === poison) {
      queue.offer(poison)
      throw ClosedWatchServiceException()
    }
    return key
  }

  protected fun checkOpen() {
    if (!open.get()) {
      throw ClosedWatchServiceException()
    }
  }

  override fun close() {
    if (open.compareAndSet(true, false)) {
      queue.clear()
      queue.offer(poison)
    }
  }

  internal data class Event<T>(
    private val kind: WatchEvent.Kind<T>,
    private val count: Int,
    private val context: T?,
  ) : WatchEvent<T> {
    init {
      require(count >= 0) { "count ($count) must be non-negative" }
    }

    override fun kind(): WatchEvent.Kind<T> = kind

    override fun count(): Int = count

    override fun context(): T? = context
  }
}

internal open class AbstractWatchKey(
  private val watcher: AbstractWatchService,
  private val watchable: Watchable?,
  @Suppress("unused") subscribedTypes: Iterable<WatchEvent.Kind<*>>,
  queueSize: Int,
) : WatchKey {
  private val state = AtomicReference(State.READY)
  private val valid = AtomicBoolean(true)
  private val overflow = AtomicInteger()
  private val events = ArrayBlockingQueue<WatchEvent<*>>(queueSize)

  fun post(event: WatchEvent<*>) {
    if (!events.offer(event)) {
      overflow.incrementAndGet()
    }
  }

  fun signal() {
    if (state.getAndSet(State.SIGNALLED) == State.READY) {
      watcher.enqueue(this)
    }
  }

  override fun isValid(): Boolean = watcher.isOpen && valid.get()

  override fun pollEvents(): List<WatchEvent<*>> {
    val result = ArrayList<WatchEvent<*>>(events.size)
    events.drainTo(result)
    val overflowCount = overflow.getAndSet(0)
    if (overflowCount != 0) {
      result.add(AbstractWatchService.Event(OVERFLOW, overflowCount, null))
    }
    return Collections.unmodifiableList(result)
  }

  override fun reset(): Boolean {
    if (isValid && state.compareAndSet(State.SIGNALLED, State.READY) && hasPendingEvents()) {
      signal()
    }
    return isValid
  }

  private fun hasPendingEvents(): Boolean = !events.isEmpty() || overflow.get() != 0

  override fun cancel() {
    valid.set(false)
    watcher.cancelled(this)
  }

  override fun watchable(): Watchable? = watchable

  fun signalEvent(kind: WatchEvent.Kind<Path>, context: Path) {
    post(AbstractWatchService.Event(kind, 1, context))
    signal()
  }

  fun signalOverflow(context: Path?) {
    post(AbstractWatchService.Event(OVERFLOW, 1, context))
    signal()
  }

  override fun toString(): String = "AbstractWatchKey{watchable=$watchable, valid=${valid.get()}}"

  private enum class State {
    READY,
    SIGNALLED,
  }
}

internal class WatchablePath(val file: Path) : Watchable {
  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    if (watcher !is AbstractWatchService) {
      throw ProviderMismatchException()
    }
    return watcher.register(this, events.asList())
  }

  override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey {
    if (!java.nio.file.Files.exists(file)) {
      throw RuntimeException("Directory to watch doesn't exist: $file")
    }
    return register(watcher, events)
  }

  override fun toString(): String = "Path{file=$file}"
}
