// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*

@ApiStatus.Internal
class VcsLogProgress(parent: Disposable) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val lock = Any()
  private val listeners = ArrayList<ProgressListener>()
  private val tasksWithVisibleProgress = HashSet<RunningProgress>()
  private val tasksWithSilentProgress = HashSet<RunningProgress>()
  private var isDisposed = false

  val isRunning: Boolean
    get() = synchronized(lock) {
      !tasksWithVisibleProgress.isEmpty()
    }

  private val runningKeys: Set<ProgressKey>
    get() = synchronized(lock) {
      tasksWithVisibleProgress.mapTo(mutableSetOf()) { it.key }
    }

  init {
    Disposer.register(parent, this)
    Disposer.register(this, disposableFlag)
  }

  fun createProgressIndicator(key: ProgressKey): ProgressIndicator {
    return createProgressIndicator(true, key)
  }

  fun createProgressIndicator(visible: Boolean, key: ProgressKey): ProgressIndicator {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return EmptyProgressIndicator()
    }
    return VcsLogProgressIndicator(visible, key)
  }

  suspend fun <T> runWithProgress(key: ProgressKey, visible: Boolean = true, action: suspend () -> T): T {
    val task = RunningCoroutine(key, visible)
    started(task)
    try {
      return action()
    }
    finally {
      stopped(task)
    }
  }

  fun addProgressIndicatorListener(listener: ProgressListener, parentDisposable: Disposable?) {
    synchronized(lock) {
      listeners.add(listener)
      if (parentDisposable != null) {
        Disposer.register(parentDisposable) { removeProgressIndicatorListener(listener) }
      }
      val keys = runningKeys
      if (runningKeys.isNotEmpty()) {
        fireNotification(listOf(listener), keys)
      }
    }
  }

  fun removeProgressIndicatorListener(listener: ProgressListener) {
    synchronized(lock) {
      listeners.remove(listener)
    }
  }

  private fun started(progress: RunningProgress) {
    synchronized(lock) {
      if (isDisposed && progress is ProgressIndicator) {
        progress.cancel()
        return
      }
      if (progress.isVisible) {
        val oldKeys = runningKeys
        tasksWithVisibleProgress.add(progress)
        keysUpdated(oldKeys)
      }
      else {
        tasksWithSilentProgress.add(progress)
      }
    }
  }

  private fun stopped(progress: RunningProgress) {
    synchronized(lock) {
      if (progress.isVisible) {
        val oldKeys = runningKeys
        tasksWithVisibleProgress.remove(progress)
        keysUpdated(oldKeys)
      }
      else {
        tasksWithSilentProgress.remove(progress)
      }
    }
  }

  private fun keysUpdated(oldKeys: Set<ProgressKey>) {
    synchronized(lock) {
      val newKeys = runningKeys
      if (oldKeys != newKeys) {
        fireNotification(listeners.toList(), newKeys)
      }
    }
  }

  private fun fireNotification(listeners: List<ProgressListener>, keys: Collection<ProgressKey>) {
    ApplicationManager.getApplication().invokeLater({
                                                      listeners.forEach {
                                                        it.progressChanged(keys)
                                                      }
                                                    }, {
                                                      disposableFlag.isDisposed()
                                                    })
  }

  override fun dispose() {
    synchronized(lock) {
      isDisposed = true
      for (task in tasksWithVisibleProgress) {
        if (task is ProgressIndicator) {
          task.cancel()
        }
      }
      for (task in tasksWithSilentProgress) {
        if (task is ProgressIndicator) {
          task.cancel()
        }
      }
    }
  }

  private interface RunningProgress {
    val key: ProgressKey
    val isVisible: Boolean
  }

  private inner class VcsLogProgressIndicator(
    override val isVisible: Boolean,
    key: ProgressKey,
  ) : ProgressIndicatorBase(), RunningProgress {
    override var key: ProgressKey = key
      get() {
        synchronized(this@VcsLogProgress.lock) {
          return field
        }
      }
      set(value) {
        synchronized(this@VcsLogProgress.lock) {
          val oldKeys = runningKeys
          field = value
          keysUpdated(oldKeys)
        }
      }

    init {
      if (!isVisible) dontStartActivity()
    }

    override fun start() {
      synchronized(this.lock) {
        super.start()
        started(this)
      }
    }

    override fun stop() {
      synchronized(this.lock) {
        super.stop()
        stopped(this)
      }
    }
  }

  private class RunningCoroutine(
    override val key: ProgressKey,
    override val isVisible: Boolean,
  ) : RunningProgress

  interface ProgressListener {
    fun progressChanged(keys: Collection<ProgressKey>)
  }

  open class ProgressKey(private val name: @NonNls String) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val key = other as ProgressKey
      return name == key.name
    }

    override fun hashCode(): Int {
      return Objects.hash(name)
    }
  }

  companion object {
    @JvmStatic
    fun updateCurrentKey(key: ProgressKey) {
      val indicator = ProgressManager.getInstance().getProgressIndicator()
      if (indicator is VcsLogProgressIndicator) {
        indicator.key = key
      }
    }
  }
}
