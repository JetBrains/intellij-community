// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.DummyVersionedEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.LinkedBlockingQueue
import kotlin.system.measureTimeMillis

class DisposableCachedValue<R : Disposable>(
  private val entityStorage: () -> VersionedEntityStorage,
  private val cachedValue: CachedValue<R>,
  private val cacheName: String = "-",
  private val project: Project,
) : Disposable {

  private var latestValue: R? = null
  private var latestStorageModificationCount: Long? = null

  @OptIn(EntityStorageInstrumentationApi::class)
  val value: R
    @Synchronized
    get() {
      val currentValue: R
      val storage = entityStorage()
      if (storage is DummyVersionedEntityStorage) {
        val storageModificationCount = (storage.current as MutableEntityStorageInstrumentation).modificationCount
        if (storageModificationCount != latestStorageModificationCount) {
          currentValue = storage.cachedValue(cachedValue)
          latestStorageModificationCount = storageModificationCount
        }
        else {
          currentValue = latestValue!!
        }
      }
      else {
        currentValue = storage.cachedValue(cachedValue)
      }

      val oldValue = latestValue
      if (oldValue !== currentValue && oldValue != null) {
        log.debug { "Request cached value disposal. Cache name: `$cacheName`. Store type: ${storage.javaClass}. Store version: ${storage.version}" }
        CachedValuesDisposer.getInstance(project).requestDispose(oldValue)
      }
      latestValue = currentValue

      return currentValue
    }

  override fun dispose() {
    dropCache()
  }

  @Synchronized
  fun dropCache() {
    val oldValue = latestValue
    if (oldValue != null) {
      entityStorage().clearCachedValue(cachedValue)
      Disposer.dispose(oldValue)
      latestStorageModificationCount = null
      latestValue = null
    }
  }

  companion object {
    private val log = logger<DisposableCachedValue<*>>()
  }
}

/**
 * This service processes a queue of values that should be disposed.
 * The values will be disposed on the next write action caused by change of the workspace model.
 * This approach is rather hacky and should be replaced with a different solution based on Workspace Model read trace
 */
@Service(Service.Level.PROJECT)
class CachedValuesDisposer(project: Project) : Disposable {
  private val toBeDisposedValues = LinkedBlockingQueue<Disposable>()

  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        ThreadingAssertions.assertWriteAccess()
        disposeValuesInQueue()
      }
    })
  }

  // On my pretty fast computer (mac m2 max), the 2500 items are disposed in 15-20 ms.
  //   what seems to be acceptable.
  // However, we have a goal to get rid of DisposableCachedValue that is reset on every workspace model change
  //   and replace it with an implementation that disposes value only on change of the associated entity
  private fun disposeValuesInQueue() {
    val currentValues = ArrayList<Disposable>()
    val time = measureTimeMillis {
      toBeDisposedValues.drainTo(currentValues)
      currentValues.forEach { disposable -> Disposer.dispose(disposable) }
    }
    log.debug { "Disposing ${currentValues.size} cached values in $time ms" }
  }

  fun requestDispose(disposable: Disposable) {
    toBeDisposedValues.add(disposable)
  }

  override fun dispose() {
    val currentValues = ArrayList<Disposable>()
    toBeDisposedValues.drainTo(currentValues)
    currentValues.forEach { disposable -> Disposer.dispose(disposable) }
  }

  companion object {
    fun getInstance(project: Project): CachedValuesDisposer = project.service()
    val log = logger<CachedValuesDisposer>()
  }
}
