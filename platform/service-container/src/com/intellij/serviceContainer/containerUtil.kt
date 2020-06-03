// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.pico.DefaultPicoContainer
import org.jetbrains.annotations.ApiStatus
import org.picocontainer.PicoContainer

@ApiStatus.Internal
fun <T : Any> processComponentInstancesOfType(container: PicoContainer, baseClass: Class<T>, processor: (T) -> Unit) {
  // we must use instances only from our adapter (could be service or something else)
  for (adapter in (container as DefaultPicoContainer).unsafeGetAdapters()) {
    if (adapter is MyComponentAdapter && baseClass.isAssignableFrom(adapter.componentImplementation)) {
      @Suppress("UNCHECKED_CAST")
      processor((adapter.getInitializedInstance() ?: continue) as T)
    }
  }
}

@ApiStatus.Internal
fun processProjectComponents(container: PicoContainer, @Suppress("DEPRECATION") processor: (com.intellij.openapi.components.ProjectComponent, PluginDescriptor) -> Unit) {
  // we must use instances only from our adapter (could be service or something else)
  // unsafeGetAdapters should be not used here as ProjectManagerImpl uses it to call projectOpened
  for (adapter in (container as DefaultPicoContainer).componentAdapters) {
    if (adapter is MyComponentAdapter) {
      @Suppress("DEPRECATION")
      val instance = adapter.getInitializedInstance() as? com.intellij.openapi.components.ProjectComponent ?: continue
      processor(instance, adapter.pluginDescriptor)
    }
  }
}

@ApiStatus.Internal
fun processAllImplementationClasses(container: PicoContainer, processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Boolean) {
  for (o in (container as DefaultPicoContainer).unsafeGetAdapters()) {
    var aClass: Class<*>
    if (o is ServiceComponentAdapter) {
      val pluginDescriptor = o.pluginDescriptor
      // avoid delegation creation & class initialization
      aClass = try {
        if (o.isImplementationClassResolved()) {
          o.getImplementationClass()
        }
        else {
          Class.forName(o.descriptor.implementation, false, pluginDescriptor.pluginClassLoader)
        }
      }
      catch (e: Throwable) {
        // well, component registered, but required jar is not added to classpath (community edition or junior IDE)
        LOG.warn(e)
        continue
      }

      if (!processor(aClass, pluginDescriptor)) {
        break
      }
    }
    else if (o !is ExtensionComponentAdapter) {
      val pluginDescriptor = if (o is BaseComponentAdapter) o.pluginDescriptor else null
      // allow InstanceComponentAdapter without pluginId to test
      if (pluginDescriptor != null || o is DefaultPicoContainer.InstanceComponentAdapter) {
        aClass = try {
          o.componentImplementation
        }
        catch (e: Throwable) {
          LOG.warn(e)
          continue
        }

        if (!processor(aClass, pluginDescriptor)) {
          break
        }
      }
    }
  }
}

@ApiStatus.Internal
fun isWorkspaceComponent(container: PicoContainer, componentImplementation: Class<*>?): Boolean {
  for (adapter in (container as DefaultPicoContainer).unsafeGetAdapters()) {
    if (adapter is MyComponentAdapter && adapter.componentImplementation === componentImplementation) {
      return adapter.isWorkspaceComponent
    }
  }
  return false
}

internal fun checkCanceledIfNotInClassInit() {
  try {
    ProgressManager.checkCanceled()
  }
  catch (e: ProcessCanceledException) {
    // otherwise ExceptionInInitializerError happens and the class is screwed forever
    @Suppress("SpellCheckingInspection")
    if (!e.stackTrace.any { it.methodName == "<clinit>" }) {
      throw e
    }
  }
}
