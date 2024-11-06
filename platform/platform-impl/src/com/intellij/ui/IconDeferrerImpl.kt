// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import javax.swing.Icon

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
  }

  // Due to a critical bug (https://youtrack.jetbrains.com/issue/IDEA-320644/Improve-Smart-PSI-pointer-equals-implementation),
  // we are not using "caffeine".
  // Furthermore,  a size-bounded cache is unnecessary for us because our application has frequent cache clearances,
  // such as during PSI modifications.
  private val iconCache = ConcurrentHashMap<Any, Icon>()
  @Volatile
  private var mightBePopulated = false // used to avoid multiple calls PHM#clear() which might be expensive, no need to be atomic or something else
  private val lastClearTimestamp = LongAdder()

  init {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener(::clearCache))
    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        clearCache()
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        clearCache()
      }
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener { clearCache() })

    val lowMemoryWatcher = LowMemoryWatcher.register(::clearCache)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      connection.disconnect()
      lowMemoryWatcher.stop()
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
