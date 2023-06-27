// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.picocontainer.ComponentAdapter
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

@OptIn(ExperimentalCoroutinesApi::class)
internal sealed class BaseComponentAdapter(
  @JvmField internal val componentManager: ComponentManagerImpl,
  @JvmField val pluginDescriptor: PluginDescriptor,
  private val deferred: CompletableDeferred<Any>,
  private var implementationClass: Class<*>?,
) : ComponentAdapter {
  companion object {
    private val IS_DEFERRED_PREPARED: VarHandle
    private val INITIALIZING: VarHandle

    init {
      val lookup = MethodHandles.privateLookupIn(BaseComponentAdapter::class.java, MethodHandles.lookup())
      IS_DEFERRED_PREPARED = lookup.findVarHandle(BaseComponentAdapter::class.java, "isDeferredPrepared", Boolean::class.javaPrimitiveType)
      INITIALIZING = lookup.findVarHandle(BaseComponentAdapter::class.java, "initializing", Boolean::class.javaPrimitiveType)
    }
  }

  @Suppress("unused")
  private var isDeferredPrepared = false

  @Suppress("unused")
  private var initializing = false

  val pluginId: PluginId
    get() = pluginDescriptor.pluginId

  protected abstract val implementationClassName: String

  protected abstract fun isImplementationEqualsToInterface(): Boolean

  final override fun getComponentImplementation(): Class<*> = getImplementationClass()

  @Synchronized
  fun getImplementationClass(): Class<*> {
    var result = implementationClass
    if (result == null) {
      try {
        result = componentManager.loadClass<Any>(implementationClassName, pluginDescriptor)
      }
      catch (e: ClassNotFoundException) {
        throw PluginException("Failed to load class: $implementationClassName", e, pluginDescriptor.pluginId)
      }
      implementationClass = result
    }
    return result
  }

  fun getInitializedInstance(): Any? = if (deferred.isCompleted) deferred.getCompleted() else null

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use")
  final override fun getComponentInstance(): Any? {
    //LOG.error("Use getInstance() instead")
    return getInstance(componentManager, null)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getInstance(componentManager: ComponentManagerImpl, keyClass: Class<T>?, createIfNeeded: Boolean = true): T? {
    if (deferred.isCompleted) {
      return deferred.getCompleted() as T
    }
    else if (!createIfNeeded) {
      return null
    }

    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    checkCanceledIfNotInClassInit()
    checkContainerIsActive(componentManager)

    val activityCategory = if (StartUpMeasurer.isEnabled()) getActivityCategory(componentManager) else null
    val beforeLockTime = if (activityCategory == null) -1 else StartUpMeasurer.getCurrentTime()

    if (IS_DEFERRED_PREPARED.compareAndSet(this, false, true)) {
      return createInstance(keyClass, componentManager, activityCategory)
    }

    // without this check, will be a deadlock if during createInstance we call createInstance again (cyclic initialization)
    if (Thread.holdsLock(this)) {
      throw PluginException("Cyclic service initialization: ${toString()}", pluginId)
    }

    if (EDT.isCurrentThreadEdt()) {
      while (!deferred.isCompleted) {
        ProgressManager.checkCanceled()
        try {
          Thread.sleep(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
        }
        catch (e: InterruptedException) {
          throw ProcessCanceledException(e)
        }
      }
    }
    else if (Cancellation.isInNonCancelableSection()) {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        deferred.join()
      }
    }
    else {
      runBlockingMaybeCancellable {
        deferred.join()
      }
    }

    val result = deferred.getCompleted() as T
    if (activityCategory != null) {
      val end = StartUpMeasurer.getCurrentTime()
      if ((end - beforeLockTime) > 100) {
        // Do not report plugin id, not clear who calls us and how we should interpret this delay.
        // Total duration vs own duration is enough for plugin cost measurement.
        StartUpMeasurer.addCompletedActivity(
          beforeLockTime, end, implementationClassName,
          ActivityCategory.SERVICE_WAITING, /* pluginId = */ null
        )
      }
    }
    return result
  }

  @Synchronized
  private fun <T : Any> createInstance(keyClass: Class<T>?,
                                       componentManager: ComponentManagerImpl,
                                       activityCategory: ActivityCategory?): T {
    check(!deferred.isCompleted)
    check(INITIALIZING.compareAndSet(this, false, true)) {
      PluginException("Cyclic service initialization: ${toString()}", pluginId)
    }

    return Cancellation.withNonCancelableSection().use {
      doCreateInstance(keyClass = keyClass, componentManager = componentManager, activityCategory = activityCategory)
    }
  }

  private fun <T : Any> doCreateInstance(
    keyClass: Class<T>?,
    componentManager: ComponentManagerImpl,
    activityCategory: ActivityCategory?,
  ): T {
    try {
      val startTime = StartUpMeasurer.getCurrentTime()
      val implementationClass: Class<T>
      if (keyClass != null && isImplementationEqualsToInterface()) {
        implementationClass = keyClass
        this.implementationClass = keyClass
      }
      else {
        @Suppress("UNCHECKED_CAST")
        implementationClass = getImplementationClass() as Class<T>
      }

      val instance = doCreateInstance(componentManager, implementationClass)
      activityCategory?.let { category ->
        val end = StartUpMeasurer.getCurrentTime()
        if (activityCategory != ActivityCategory.MODULE_SERVICE || (end - startTime) > StartUpMeasurer.MEASURE_THRESHOLD) {
          StartUpMeasurer.addCompletedActivity(startTime, end, implementationClassName, category, pluginId.idString)
        }
      }

      deferred.complete(instance)
      return instance
    }
    catch (e: Throwable) {
      deferred.completeExceptionally(e)
      throw e
    }
    finally {
      INITIALIZING.set(this, false)
    }
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun <T : Any> getInstanceAsync(componentManager: ComponentManagerImpl, keyClass: Class<T>?): T {
    return withContext(NonCancellable) {
      if (!IS_DEFERRED_PREPARED.compareAndSet(this@BaseComponentAdapter, false, true)) {
        return@withContext (deferred as Deferred<T>).await()
      }

      createInstance(
        keyClass = keyClass,
        componentManager = componentManager,
        activityCategory = if (StartUpMeasurer.isEnabled()) getActivityCategory(componentManager) else null,
      )
    }
  }

  private fun checkContainerIsActive(componentManager: ComponentManagerImpl) {
    if (componentManager.isDisposed) {
      throwAlreadyDisposedError(toString(), componentManager)
    }
    if (!isGettingServiceAllowedDuringPluginUnloading(pluginDescriptor)) {
      componentManager.componentContainerIsReadonly?.let {
        val error = AlreadyDisposedException(
          "Cannot create ${toString()} because container in read-only mode (reason=$it, container=${componentManager})"
        )
        throw if (!isUnderIndicatorOrJob()) error else ProcessCanceledException(error)
      }
    }
  }

  internal fun throwAlreadyDisposedError(componentManager: ComponentManagerImpl) {
    throwAlreadyDisposedError(toString(), componentManager)
  }

  protected abstract fun getActivityCategory(componentManager: ComponentManagerImpl): ActivityCategory?

  protected abstract fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T
}