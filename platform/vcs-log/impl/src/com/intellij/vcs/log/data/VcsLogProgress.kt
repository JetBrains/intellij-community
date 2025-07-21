// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.NonNls
import java.util.*

class VcsLogProgress(parent: Disposable) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val lock = Any()
  private val listeners = ArrayList<ProgressListener>()
  private val tasksWithVisibleProgress = HashSet<VcsLogProgressIndicator>()
  private val tasksWithSilentProgress = HashSet<ProgressIndicator>()
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

  fun addProgressIndicatorListener(listener: ProgressListener, parentDisposable: Disposable?) {
    synchronized(lock) {
      listeners.add(listener)
      if (parentDisposable != null) {
        Disposer.register(parentDisposable) { removeProgressIndicatorListener(listener) }
      }
      if (isRunning) {
        val keys = runningKeys
        ApplicationManager.getApplication().invokeLater { listener.progressStarted(keys) }
      }
    }
  }

  fun removeProgressIndicatorListener(listener: ProgressListener) {
    synchronized(lock) {
      listeners.remove(listener)
    }
  }

  private fun started(indicator: VcsLogProgressIndicator) {
    synchronized(lock) {
      if (isDisposed) {
        indicator.cancel()
        return
      }
      if (indicator.isVisible) {
        val oldKeys = runningKeys
        tasksWithVisibleProgress.add(indicator)
        if (tasksWithVisibleProgress.size == 1) {
          val key = indicator.key
          fireNotification { it.progressStarted(setOf(key)) }
        }
        else {
          keysUpdated(oldKeys)
        }
      }
      else {
        tasksWithSilentProgress.add(indicator)
      }
    }
  }

  private fun stopped(indicator: VcsLogProgressIndicator) {
    synchronized(lock) {
      if (indicator.isVisible) {
        tasksWithVisibleProgress.remove(indicator)
        if (tasksWithVisibleProgress.isEmpty()) {
          fireNotification { it.progressStopped() }
        }
      }
      else {
        tasksWithSilentProgress.remove(indicator)
      }
    }
  }

  private fun keysUpdated(oldKeys: Set<ProgressKey>) {
    synchronized(lock) {
      val newKeys = runningKeys
      if (oldKeys != newKeys) {
        fireNotification { it.progressChanged(newKeys) }
      }
    }
  }

  private fun fireNotification(action: (ProgressListener) -> Unit) {
    synchronized(lock) {
      val list = listeners.toList()
      ApplicationManager.getApplication().invokeLater({ list.forEach(action) }, { disposableFlag.isDisposed() })
    }
  }

  override fun dispose() {
    synchronized(lock) {
      isDisposed = true
      for (indicator in tasksWithVisibleProgress) {
        indicator.cancel()
      }
      for (indicator in tasksWithSilentProgress) {
        indicator.cancel()
      }
    }
  }

  private inner class VcsLogProgressIndicator(val isVisible: Boolean, key: ProgressKey) : ProgressIndicatorBase() {
    var key: ProgressKey = key
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

  interface ProgressListener {
    fun progressStarted(keys: Collection<ProgressKey>)

    fun progressChanged(keys: Collection<ProgressKey>)

    fun progressStopped()
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
