// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.cancelOnDispose
import com.intellij.util.io.storage.HeavyProcessLatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/**
 * Helper class to identify when a heavy process is finished, and there is no power save mode at the same time,
 * so that it would be possible to start a resource-consuming task.
 *
 * @property project target [Project]
 * @property delayMs ignore heavy-mode switching shorter than [delayMs]
 * @property parent [Disposable] parent
 */
abstract class HeavyAwareListener(private val project: Project,
                                  private val delayMs: Int,
                                  private val parent: Disposable) {

  @Volatile
  private var _isHeavy = HeavyProcessLatch.INSTANCE.isRunning || PowerSaveMode.isEnabled()
  protected open val isHeavy get() = _isHeavy

  fun start() {
    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope
      .launch(CoroutineName("Vcs Log Heavy Process and Power Save Mode Tracker")) {
        val heavyFlow = combine(project.powerSaveModeFlow(), heavyProcessFlow(delayMs.toLong())) { values ->
          values.fold(false, Boolean::or)
        }.distinctUntilChanged()
        heavyFlow.collect { heavyValue ->
          _isHeavy = heavyValue
          if (heavyValue) heavyActivityStarted() else heavyActivityEnded()
        }
      }
    job.cancelOnDispose(parent)
  }

  abstract fun heavyActivityStarted()

  abstract fun heavyActivityEnded()
}

private fun Project.powerSaveModeFlow(): Flow<Boolean> {
  return callbackFlow {
    val listener = PowerSaveMode.Listener { trySend(PowerSaveMode.isEnabled()) }
    val listenerDisposable = Disposer.newDisposable()
    messageBus.connect(listenerDisposable).subscribe(PowerSaveMode.TOPIC, listener)

    trySend(PowerSaveMode.isEnabled())

    awaitClose { Disposer.dispose(listenerDisposable) }
  }.distinctUntilChanged()
}

@OptIn(FlowPreview::class)
private fun heavyProcessFlow(delayMs: Long): Flow<Boolean> {
  return callbackFlow {
    val listener = object : HeavyProcessLatch.HeavyProcessListener {
      override fun processStarted(op: HeavyProcessLatch.Operation) {
        trySend(true)
      }

      override fun processFinished(op: HeavyProcessLatch.Operation) {
        trySend(HeavyProcessLatch.INSTANCE.isRunning)
      }
    }
    val listenerDisposable = Disposer.newDisposable()
    HeavyProcessLatch.INSTANCE.addListener(listenerDisposable, listener)

    trySend(HeavyProcessLatch.INSTANCE.isRunning)

    awaitClose { Disposer.dispose(listenerDisposable) }
  }.distinctUntilChanged().debounce(delayMs)
}