// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.PluginId
import org.picocontainer.ComponentAdapter
import org.picocontainer.defaults.AmbiguousComponentResolutionException
import java.lang.reflect.Constructor

@Suppress("SpellCheckingInspection")
private val badAppLevelClasses = setOf(
  "de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor",
  "de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor",
  "de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor",
  "de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor",
  "de.plushnikov.intellij.plugin.processor.field.FieldNameConstantsFieldProcessor",
  "de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor",
  "com.intellij.execution.executors.DefaultDebugExecutor",
  "org.apache.http.client.HttpClient",
  "org.apache.http.impl.client.CloseableHttpClient"
)

internal class ConstructorParameterResolver {
  fun isResolvable(componentManager: PlatformComponentManagerImpl,
                   requestorKey: Any,
                   requestorClass: Class<*>,
                   requestorConstructor: Constructor<*>,
                   expectedType: Class<*>,
                   pluginId: PluginId,
                   isExtensionSupported: Boolean): Boolean {
    if (isLightService(expectedType) ||
        expectedType === ComponentManager::class.java ||
        findTargetAdapter(componentManager, expectedType, requestorKey, requestorClass, requestorConstructor, pluginId) != null) {
      return true
    }
    return isExtensionSupported && componentManager.extensionArea.findExtensionByClass(expectedType) != null
  }

  fun resolveInstance(componentManager: PlatformComponentManagerImpl,
                      requestorKey: Any,
                      requestorClass: Class<*>,
                      requestorConstructor: Constructor<*>,
                      expectedType: Class<*>,
                      pluginId: PluginId): Any? {
    if (expectedType === ComponentManager::class.java) {
      return componentManager
    }

    if (isLightService(expectedType)) {
      return componentManager.getLightService(expectedType, true)
    }

    val adapter = findTargetAdapter(componentManager, expectedType, requestorKey, requestorClass, requestorConstructor, pluginId)
                  ?: return handleUnsatisfiedDependency(componentManager, requestorClass, expectedType, pluginId)
    return when {
      adapter is BaseComponentAdapter -> {
        // project level service Foo wants application level service Bar - adapter component manager should be used instead of current
        adapter.getInstance(adapter.componentManager)
      }
      componentManager.parent == null -> adapter.getComponentInstance(componentManager.picoContainer)
      else -> componentManager.picoContainer.getComponentInstance(adapter.componentKey)
    }
  }

  private fun handleUnsatisfiedDependency(componentManager: PlatformComponentManagerImpl, requestorClass: Class<*>, expectedType: Class<*>, pluginId: PluginId): Any? {
    val extension = componentManager.extensionArea.findExtensionByClass(expectedType) ?: return null
    val message = "Do not use constructor injection to get extension instance (requestorClass=${requestorClass.name}, extensionClass=${expectedType.name})"
    val app = componentManager.getApplication()
    @Suppress("SpellCheckingInspection")
    if (app != null && app.isUnitTestMode && pluginId.idString != "org.jetbrains.kotlin" && pluginId.idString != "Lombook Plugin") {
      throw PluginException(message, pluginId)
    }
    else {
      LOG.warn(message)
    }
    return extension
  }

  private fun findTargetAdapter(componentManager: PlatformComponentManagerImpl,
                                expectedType: Class<*>,
                                requestorKey: Any,
                                requestorClass: Class<*>,
                                requestorConstructor: Constructor<*>,
                                @Suppress("UNUSED_PARAMETER") pluginId: PluginId): ComponentAdapter? {
    val container = componentManager.picoContainer
    val byKey = container.getComponentAdapter(expectedType)
    if (byKey != null && requestorKey != byKey.componentKey) {
      return byKey
    }

    val className = expectedType.name
    if (container.parent == null) {
      if (className == "com.intellij.openapi.project.Project" || badAppLevelClasses.contains(className)) {
        return null
      }
    }
    else if (className == "com.intellij.configurationStore.StreamProvider" ||
             className == "com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl" ||
             className == "com.intellij.openapi.roots.impl.CompilerModuleExtensionImpl" ||
             className == "com.intellij.openapi.roots.impl.JavaModuleExternalPathsImpl") {
      return null
    }

    if (componentManager.isGetComponentAdapterOfTypeCheckEnabled) {
      LOG.error(PluginException("getComponentAdapterOfType is used to get ${expectedType.name} (requestorClass=${requestorClass.name}, requestorConstructor=${requestorConstructor})." +
                                "\n\nProbably constructor should be marked as NonInjectable.", pluginId))
    }

    val result = container.getComponentAdaptersOfType(expectedType)
    result.removeIf { it.componentKey == requestorKey }
    return when {
      result.size == 0 -> container.parent?.getComponentAdapterOfType(expectedType)
      result.size == 1 -> result[0]
      else -> throw AmbiguousComponentResolutionException(expectedType, Array(result.size) { result[it].componentImplementation })
    }
  }
}