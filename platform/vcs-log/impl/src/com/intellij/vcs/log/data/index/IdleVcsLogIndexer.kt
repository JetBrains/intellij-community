// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.ide.IdleTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class IdleVcsLogIndexer(private val project: Project,
                                 private val index: VcsLogModifiableIndex,
                                 disposable: Disposable) {

  private val cs = CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
    Disposer.register(disposable) {
      it.cancel()
      isRunning.set(false)
    }
  }

  private val isRunning = AtomicBoolean(false)

  private var initJob: Job? = null
  private var startIndexJob: Job? = null
  private var stopIndexJob: Job? = null

  private val idleDelayValue = Registry.get("vcs.log.index.start.on.idle.delay.minutes")
    .apply {
      addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          start()
        }
      }, disposable)
    }

  fun isEnabled() = idleDelayValue.asInteger() > 0

  fun start() {
    initJob?.cancel()
    initJob = cs.childScope().launch(CoroutineName("IdleVcsLogIndexer start/stop")) {
      if (idleDelayValue.asInteger() < 0) {
        stop()
      }
      else {
        withContext(Dispatchers.Default) {
          while (!isRunning.get()) {
            if (index.needIndexing()) {
              withContext(Dispatchers.EDT) {
                doInit()
                cancel()
              }
            }

            delay(10000)
          }
        }
      }
    }
  }

  private fun stop() {
    if (isRunning.compareAndSet(true, false)) {
      startIndexJob?.cancel()
      stopIndexJob?.cancel()
    }
  }

  private fun doInit() {
    if (isRunning.compareAndSet(false, true)) {

      startIndexJob = launch("Start VCS log indexing on IDE idle", idleDelayValue.asInteger().minutes) {
        if (!index.isIndexingScheduled()) {
          VcsLogUsageTriggerCollector.idleIndexerTriggered(project)
          index.toggleIndexing()
        }
      }

      stopIndexJob = launch("Stop VCS log indexing on IDE active", 100.milliseconds) {
        if (index.isIndexingScheduled()) {
          index.toggleIndexing()
        }
      }
    }
  }

  private fun launch(name: String, timeout: Duration, runnable: () -> Unit): Job {
    @OptIn(FlowPreview::class)
    return cs.childScope().launch(CoroutineName(name)) {
      IdleTracker.getInstance().events
        .debounce(timeout)
        .collect {
          withContext(Dispatchers.EDT) {
            runnable()
          }
        }
    }
  }
}
