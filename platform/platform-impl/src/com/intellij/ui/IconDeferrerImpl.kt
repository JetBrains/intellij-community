// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SystemProperties
import com.intellij.util.SystemProperties.getIntProperty
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.LongAdder
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal class IconDeferrerImpl(coroutineScope: CoroutineScope) : IconDeferrer() {
  companion object {
    private val isEvaluationInProgress = ThreadLocal.withInitial { false }

    suspend inline fun <T> evaluateDeferred(crossinline runnable: suspend () -> T): T {
      try {
        isEvaluationInProgress.set(true)
        return withContext(isEvaluationInProgress.asContextElement()) {
          runnable()
        }
      }
      finally {
        isEvaluationInProgress.set(false)
      }
    }

    private val log: Logger = logger<IconDeferrerImpl>()
  }

  private val preConfiguredCacheSize = getIntProperty("ide.icons.deferrerCacheSize", 1000)

  // we restrict max cache size for cases when a user reads code only.
  @Volatile
  private var maxCacheSize: Int = preConfiguredCacheSize
  // Due to a critical bug (https://youtrack.jetbrains.com/issue/IDEA-320644/Improve-Smart-PSI-pointer-equals-implementation),
  // we are not using "caffeine".
  private val iconCache: ConcurrentMap<Any, Icon> = ConcurrentHashMap(min(1000, preConfiguredCacheSize))
  @Volatile
  private var mightBePopulated: Boolean = false // used to avoid multiple calls CHM#clear() which might be expensive, no need to be atomic or something else
  private val lastClearTimestamp: LongAdder = LongAdder()

  private val deferrerCacheClearingCheckPeriod: Long = SystemProperties.getLongProperty("ide.icons.deferrerCacheClearingCheckPeriod.ms", 30.seconds.toLong(DurationUnit.MILLISECONDS))

  init {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
      log.trace("clearing icon deferrer cache after psi modification")
      clearCache()
    })

    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListenerBackgroundable {
      override fun after(events: List<VFileEvent>) {
        log.trace("clearing icon deferrer cache after vfs events")
        clearCache()
        recomputeMaxCacheSize(events.size)
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        log.trace("clearing icon deferrer cache after project closing")
        clearCache()
      }
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener {
      log.trace("clearing icon deferrer cache after VirtualFileAppearanceListener event")
      clearCache()
    })

    val lowMemoryWatcher = LowMemoryWatcher.register {
      log.trace("Clearing icon deferrer cache because after low memory signal")
      clearCache()
    }

    scheduleCacheClearingTask(coroutineScope)

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      connection.disconnect()
      lowMemoryWatcher.stop()
    }
  }
  private fun recomputeMaxCacheSize(neededSize: Int) {
    if (maxCacheSize < neededSize) {
      // in case of modifications across the very big project it make sense to increase the cache size,
      // but make sure to not exceed sensible threshold (10K icons here) and not descend below the configured "ide.icons.deferrerCacheSize=" value
      maxCacheSize = max(preConfiguredCacheSize, min(10_000, neededSize))
    }
  }

  /** clears cache once in a while if the cache gets too big */
  private fun scheduleCacheClearingTask(coroutineScope: CoroutineScope) {
    coroutineScope.launch {
      while (true) {
        delay(deferrerCacheClearingCheckPeriod)
        val currentCacheSize = iconCache.size
        recomputeMaxCacheSize(currentCacheSize)
        if (currentCacheSize > maxCacheSize) {
          log.trace { "Clearing icon deferrer cache because it's too big: $currentCacheSize > $maxCacheSize" }
          clearCache()
        }
        else {
          log.trace { "icon cache size $currentCacheSize" }
        }
      }
    }
  }

  override fun clearCache() {
    lastClearTimestamp.increment()
    if (mightBePopulated) {
      mightBePopulated = false
      iconCache.clear()
    }
  }

  override fun <T : Any> defer(base: Icon?, param: T, evaluator: (T) -> Icon?): Icon {
    if (isEvaluationInProgress.get()) {
      return evaluator(param) ?: DeferredIconImpl.EMPTY_ICON
    }

    val result = iconCache.computeIfAbsent(param) {
      val started = lastClearTimestamp.sum()
      DeferredIconImpl(baseIcon = base,
                       param = param,
                       needReadAction = true,
                       evaluator = evaluator,
                       listener = { source, icon ->
                         // check if our result is not outdated yet
                         if (started == lastClearTimestamp.sum()) {
                           // use `replace` instead of `put` to ensure that our result is not outdated yet
                           iconCache.replace(source.param, source, icon)
                         }
                       })
    }
    mightBePopulated = true
    return result
  }

  override fun <T : Any> deferAsync(base: Icon?, param: T, evaluator: suspend (T) -> Icon?): Icon {
    val result = iconCache.computeIfAbsent(param) {
      DeferredIconImpl(baseIcon = base,
                       param = param,
                       asyncEvaluator = { evaluator(it) ?: DeferredIconImpl.EMPTY_ICON },
                       listener = { source, icon ->
                         iconCache.replace(source.param, source, icon)
                       })
    }
    mightBePopulated = true
    return result
  }
}
