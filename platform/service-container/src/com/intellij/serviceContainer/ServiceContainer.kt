// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.LoadingPhase
import com.intellij.diagnostic.ParallelActivity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Level
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.pico.DefaultPicoContainer
import org.picocontainer.PicoContainer
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentMap

private val LOG = logger<ServiceManager>()

// used only for Application for now
internal class ServiceContainer(parent: PicoContainer?) : DefaultPicoContainer(parent) {
  private val lightServices: ConcurrentMap<Class<*>, Any>? = if (parent == null) ContainerUtil.newConcurrentMap() else null

  override fun <T : Any> getService(serviceClass: Class<T>, isCreate: Boolean): T? {
    val lightServices = lightServices
    if (lightServices == null || !isLightService(serviceClass)) {
      return super.getService(serviceClass, isCreate)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      val result = lightServices.get(serviceClass) as T?
      if (result != null || !isCreate) {
        return result
      }
      else {
        synchronized(serviceClass) {
          return getOrCreateLightService(serviceClass, lightServices)
        }
      }
    }
  }
}

private fun <T : Any> getOrCreateLightService(serviceClass: Class<T>, cache: ConcurrentMap<Class<*>, Any>): T {
  LoadingPhase.COMPONENT_REGISTERED.assertAtLeast()

  @Suppress("UNCHECKED_CAST")
  var instance = cache.get(serviceClass) as T?
  if (instance != null) {
    return instance
  }

  val componentManager = ApplicationManager.getApplication()
  HeavyProcessLatch.INSTANCE.processStarted("Creating service '${serviceClass.name}'").use {
    if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
      instance = createLightService(serviceClass, componentManager)
    }
    else {
      ProgressManager.getInstance().executeNonCancelableSection {
        instance = createLightService(serviceClass, componentManager)
      }
    }
  }

  val prevValue = cache.put(serviceClass, instance)
  LOG.assertTrue(prevValue == null)
  return instance!!
}

private fun <T : Any> createLightService(serviceClass: Class<T>, componentManager: ComponentManager): T {
  val startTime = StartUpMeasurer.getCurrentTime()
  val instance = ReflectionUtil.newInstance(serviceClass, false)
  if (instance is Disposable) {
    Disposer.register(componentManager, instance as Disposable)
  }
  componentManager.initializeComponent(instance, null)
  ParallelActivity.SERVICE.record(startTime, instance.javaClass, Level.APPLICATION)
  return instance
}

private fun <T> isLightService(serviceClass: Class<T>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}