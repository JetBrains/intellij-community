// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import java.lang.reflect.Modifier

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

class PrecomputedExtensionModel(
  @JvmField internal val extensionPoints: List<List<ExtensionPointDescriptor>>,
  @JvmField internal val pluginDescriptors: List<IdeaPluginDescriptor>,
  @JvmField internal val extensionPointTotalCount: Int,

  @JvmField internal val nameToExtensions: Map<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>
)

fun precomputeExtensionModel(plugins: List<IdeaPluginDescriptorImpl>): PrecomputedExtensionModel {
  val extensionPointDescriptors = ArrayList<List<ExtensionPointDescriptor>>()
  val pluginDescriptors = ArrayList<IdeaPluginDescriptor>()
  var extensionPointTotalCount = 0
  val containerSelector: (pluginDescriptor: IdeaPluginDescriptorImpl) -> ContainerDescriptor = { it.moduleContainerDescriptor }
  val nameToExtensions = HashMap<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>()

  // step 1 - collect container level extension points
  for (plugin in plugins) {
    executeRegisterTask(plugin, containerSelector(plugin), containerSelector) { pluginDescriptor, containerDescriptor ->
      containerDescriptor.extensionPoints?.let {
        extensionPointDescriptors.add(it)
        pluginDescriptors.add(pluginDescriptor)
        extensionPointTotalCount += it.size

        for (descriptor in it) {
          nameToExtensions.put(descriptor.getQualifiedName(pluginDescriptor), mutableListOf())
        }
      }
    }
  }

  // step 2 - collect container level extensions
  for (plugin in plugins) {
    executeRegisterTask(plugin, containerSelector(plugin), containerSelector) { pluginDescriptor, _ ->
      val unsortedMap = pluginDescriptor.epNameToExtensions ?: return@executeRegisterTask
      for ((name, list) in unsortedMap.entries) {
        nameToExtensions.get(name)?.add(pluginDescriptor to list)
      }
    }
  }

  return PrecomputedExtensionModel(
    extensionPoints = extensionPointDescriptors,
    pluginDescriptors = pluginDescriptors,
    extensionPointTotalCount = extensionPointTotalCount,

    nameToExtensions = nameToExtensions,
  )
}

internal inline fun executeRegisterTask(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                                        mainContainerDescriptor: ContainerDescriptor,
                                        containerSelector: (pluginDescriptor: IdeaPluginDescriptorImpl) -> ContainerDescriptor,
                                        crossinline task: (IdeaPluginDescriptorImpl, ContainerDescriptor) -> Unit) {
  task(mainPluginDescriptor, mainContainerDescriptor)
  for (dep in mainPluginDescriptor.pluginDependencies) {
    if (dep.isDisabledOrBroken) {
      continue
    }

    val subPluginDescriptor = dep.subDescriptor ?: continue
    task(subPluginDescriptor, containerSelector(subPluginDescriptor))

    for (subDep in subPluginDescriptor.pluginDependencies) {
      if (!subDep.isDisabledOrBroken) {
        val d = subDep.subDescriptor ?: continue
        task(d, containerSelector(d))
        assert(d.pluginDependencies.isEmpty() || d.pluginDependencies.all { it.subDescriptor == null })
      }
    }
  }
}


internal fun isGettingServiceAllowedDuringPluginUnloading(descriptor: PluginDescriptor): Boolean {
  return descriptor.isRequireRestart ||
         descriptor.pluginId == PluginManagerCore.CORE_ID || descriptor.pluginId == PluginManagerCore.JAVA_PLUGIN_ID
}

internal fun throwAlreadyDisposedError(serviceDescription: String, componentManager: ComponentManagerImpl, indicator: ProgressIndicator?) {
  val error = AlreadyDisposedException("Cannot create $serviceDescription because container is already disposed (container=${componentManager})")
  if (indicator == null) {
    throw error
  }
  else {
    throw ProcessCanceledException(error)
  }
}

internal fun isLightService(serviceClass: Class<*>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}