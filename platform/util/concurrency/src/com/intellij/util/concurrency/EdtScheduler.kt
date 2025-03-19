// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Use coroutines.
 */
@Internal
@Obsolete
@Service
class EdtScheduler(@JvmField val coroutineScope: CoroutineScope) {
  companion object {
    @Volatile
    private var instance: EdtScheduler? = null

    @JvmStatic
    fun getInstance(): EdtScheduler {
      var r = instance
      if (r == null) {
        r = service()
        instance = r
      }
      return r
    }

    init {
      ApplicationManager.registerCleaner { instance = null }
    }
  }

  fun schedule(delayMillis: Int, task: Runnable): Job {
    return schedule(delay = delayMillis.milliseconds, task = task, modality = ModalityState.defaultModalityState())
  }

  fun schedule(delay: Duration, task: Runnable): Job {
    return schedule(delay = delay, task = task, modality = ModalityState.defaultModalityState())
  }

  fun schedule(delay: Int, modality: ModalityState, task: Runnable): Job {
    return schedule(delay = delay.milliseconds, modality = modality, task = task)
  }

  fun schedule(delay: Duration, modality: ModalityState, task: Runnable): Job {
    return coroutineScope.launch {
      delay(delay)
      withContext(Dispatchers.EDT + modality.asContextElement()) {
        task.run()
      }
    }
  }
}