// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.Disposer
import org.picocontainer.ComponentAdapter
import org.picocontainer.PicoContainer

internal abstract class BaseComponentAdapter(internal val componentManager: ComponentManagerImpl,
                                             val pluginDescriptor: PluginDescriptor,
                                             @field:Volatile private var initializedInstance: Any?,
                                             private var implementationClass: Class<*>?) : ComponentAdapter {
  @Volatile
  private var initializing = false

  val pluginId: PluginId
    get() = pluginDescriptor.pluginId

  val isInitializing: Boolean
    get() = initializing

  protected abstract val implementationClassName: String

  protected abstract fun isImplementationEqualsToInterface(): Boolean

  final override fun getComponentImplementation() = getImplementationClass()

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

  fun getInitializedInstance() = initializedInstance

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use")
  final override fun getComponentInstance(container: PicoContainer): Any? {
    //LOG.error("Use getInstance() instead")
    return getInstance(componentManager, null)
  }

  fun <T : Any> getInstance(componentManager: ComponentManagerImpl,
                            keyClass: Class<T>?,
                            createIfNeeded: Boolean = true,
                            indicator: ProgressIndicator? = null): T? {
    // could be called during some component.dispose() call, in this case we don't attempt to instantiate
    @Suppress("UNCHECKED_CAST")
    val instance = initializedInstance as T?
    if (instance != null || !createIfNeeded) {
      return instance
    }
    return getInstanceUncached(componentManager, keyClass, indicator ?: ProgressIndicatorProvider.getGlobalProgressIndicator())
  }

  private fun <T : Any> getInstanceUncached(componentManager: ComponentManagerImpl, keyClass: Class<T>?, indicator: ProgressIndicator?): T {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()
    checkContainerIsActive(componentManager, indicator)

    val activityCategory = if (StartUpMeasurer.isEnabled()) getActivityCategory(componentManager) else null
    val beforeLockTime = if (activityCategory == null) -1 else StartUpMeasurer.getCurrentTime()

    synchronized(this) {
      @Suppress("UNCHECKED_CAST")
      var instance = initializedInstance as T?
      if (instance != null) {
        if (activityCategory != null) {
          val end = StartUpMeasurer.getCurrentTime()
          if ((end - beforeLockTime) > 100) {
            // do not report plugin id - not clear who calls us and how we should interpret this delay - total duration vs own duration is enough for plugin cost measurement
            StartUpMeasurer.addCompletedActivity(beforeLockTime, end, implementationClassName, ActivityCategory.SERVICE_WAITING, /* pluginId = */ null)
          }
        }
        return instance
      }

      if (initializing) {
        LOG.error(PluginException("Cyclic service initialization: ${toString()}", pluginId))
      }

      try {
        initializing = true

        val startTime = StartUpMeasurer.getCurrentTime()
        val implementationClass: Class<T>
        when {
          keyClass != null && isImplementationEqualsToInterface() -> {
            implementationClass = keyClass
            this.implementationClass = keyClass
          }
          else -> {
            @Suppress("UNCHECKED_CAST")
            implementationClass = getImplementationClass() as Class<T>
            // check after loading class once again
            checkContainerIsActive(componentManager, indicator)
          }
        }

        instance = doCreateInstance(componentManager, implementationClass, indicator)
        activityCategory?.let { category ->
          val end = StartUpMeasurer.getCurrentTime()
          if (activityCategory != ActivityCategory.MODULE_SERVICE || (end - startTime) > StartUpMeasurer.MEASURE_THRESHOLD) {
            StartUpMeasurer.addCompletedActivity(startTime, end, implementationClassName, category, pluginId.idString)
          }
        }

        initializedInstance = instance
        return instance
      }
      finally {
        initializing = false
      }
    }
  }

  /**
   * Indicator must be always passed - if under progress, then ProcessCanceledException will be thrown instead of AlreadyDisposedException.
   */
  private fun checkContainerIsActive(componentManager: ComponentManagerImpl, indicator: ProgressIndicator?) {
    if (indicator != null) {
      checkCanceledIfNotInClassInit()
    }

    if (componentManager.isDisposed) {
      throwAlreadyDisposedError(toString(), componentManager, indicator)
    }
    if (!isGettingServiceAllowedDuringPluginUnloading(pluginDescriptor)) {
      componentManager.componentContainerIsReadonly?.let {
        val error = AlreadyDisposedException("Cannot create ${toString()} because container in read-only mode (reason=$it, container=${componentManager})")
        throw if (indicator == null) error else ProcessCanceledException(error)
      }
    }
  }

  internal fun throwAlreadyDisposedError(componentManager: ComponentManagerImpl, indicator: ProgressIndicator?) {
    throwAlreadyDisposedError(toString(), componentManager, indicator)
  }

  protected abstract fun getActivityCategory(componentManager: ComponentManagerImpl): ActivityCategory?

  protected abstract fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>, indicator: ProgressIndicator?): T

  @Synchronized
  fun <T : Any> replaceInstance(keyAsClass: Class<*>,
                                instance: T,
                                parentDisposable: Disposable?,
                                hotCache: MutableMap<Class<*>, Any?>?): T? {
    val old = initializedInstance
    initializedInstance = instance
    hotCache?.put(keyAsClass, instance)

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, Disposable {
        synchronized(this) {
          @Suppress("DEPRECATION")
          if (initializedInstance === instance && instance is Disposable && !Disposer.isDisposed(instance)) {
            Disposer.dispose(instance)
          }
          initializedInstance = old
          if (hotCache != null) {
            if (old == null) {
              hotCache.remove(keyAsClass)
            }
            else {
              hotCache.put(keyAsClass, old)
            }
          }
        }
      })
    }

    @Suppress("UNCHECKED_CAST")
    return old as T?
  }
}