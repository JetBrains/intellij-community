// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ComponentManager
import com.intellij.util.pico.DefaultPicoContainer
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
  "com.intellij.serviceContainer.BarImpl"
)

internal class ConstructorParameterResolver(private val isExtensionSupported: Boolean) {
  fun isResolvable(componentManager: PlatformComponentManagerImpl,
                        requestorKey: Any,
                        requestorClass: Class<*>,
                        requestorConstructor: Constructor<*>,
                        expectedType: Class<*>): Boolean {
    if (isLightService(expectedType) || expectedType === ComponentManager::class.java || resolveAdapter(componentManager, requestorKey, requestorClass, requestorConstructor, expectedType) != null) {
      return true
    }
    return isExtensionSupported && componentManager.extensionArea.findExtensionByClass(expectedType) != null
  }

   fun resolveInstance(componentManager: PlatformComponentManagerImpl,
                       requestorKey: Any,
                       requestorClass: Class<*>,
                       requestorConstructor: Constructor<*>,
                       expectedType: Class<*>): Any? {
    if (expectedType === ComponentManager::class.java) {
      return componentManager
    }

    if (isLightService(expectedType)) {
      return componentManager.getLightService(expectedType, true)
    }

    val adapter = resolveAdapter(componentManager, requestorKey, requestorClass, requestorConstructor, expectedType)
                  ?: return handleUnsatisfiedDependency(componentManager, requestorClass, expectedType)
    return when (adapter) {
      is BaseComponentAdapter -> {
        // project level service Foo wants application level service Bar - adapter component manager should be used instead of current
        adapter.getInstance(adapter.componentManager)
      }
      else -> {
        if (componentManager.parent == null) {
          adapter.getComponentInstance(componentManager.picoContainer)
        }
        else {
          componentManager.picoContainer.getComponentInstance(adapter.componentKey)
        }
      }
    }
  }

  private fun handleUnsatisfiedDependency(componentManager: PlatformComponentManagerImpl, requestorClass: Class<*>, expectedType: Class<*>): Any? {
    if (isExtensionSupported) {
      val extension = componentManager.extensionArea.findExtensionByClass(expectedType)
      if (extension != null) {
        LOG.warn("Do not use constructor injection to get extension instance (requestorClass=${requestorClass.name}, extensionClass=${expectedType.name})")
      }
      return extension
    }

    throw RuntimeException("${requestorClass.name} has unsatisfied dependency: ${expectedType.name}")
  }

  private fun resolveAdapter(componentManager: PlatformComponentManagerImpl,
                             requestorKey: Any,
                             requestorClass: Class<*>,
                             requestorConstructor: Constructor<*>,
                             expectedType: Class<*>): ComponentAdapter? {
    val result = getTargetAdapter(componentManager.picoContainer, expectedType, requestorKey, requestorClass, requestorConstructor) ?: return null
    if (expectedType.isAssignableFrom(result.componentImplementation)) {
      return result
    }
    return null
  }

  private fun getTargetAdapter(container: DefaultPicoContainer,
                               expectedType: Class<*>,
                               requestorKey: Any,
                               requestorClass: Class<*>,
                               requestorConstructor: Constructor<*>): ComponentAdapter? {
    val byKey = container.getComponentAdapter(expectedType)
    if (byKey != null && requestorKey != byKey.componentKey) {
      return byKey
    }

    // see UndoManagerImpl / RunManager / JavaModuleExternalPathsImpl for example
    val className = expectedType.name

    if (container.parent == null) {
      if (className == "com.intellij.openapi.project.Project" || badAppLevelClasses.contains(className)) {
        return null
      }
    }
    else {
      if (className == "com.intellij.configurationStore.StreamProvider" ||
          className == "com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl" ||
          className == "com.intellij.openapi.roots.impl.CompilerModuleExtensionImpl" ||
          className == "com.intellij.openapi.roots.impl.JavaModuleExternalPathsImpl") {
        return null
      }
    }

    if (isNotApplicableClass(expectedType)) {
      return null
    }

    LOG.error("getComponentAdapterOfType is used to get ${expectedType.name} (requestorClass=${requestorClass.name}, requestorConstructor=${requestorConstructor})." + "\n\nProbably constructor should be marked as NonInjectable.")

    val found = container.getComponentAdaptersOfType(expectedType)
    found.removeIf { it.componentKey == requestorKey }
    return when {
      found.size == 0 -> container.parent?.getComponentAdapterOfType(expectedType)
      found.size == 1 -> found[0]
      else -> throw AmbiguousComponentResolutionException(expectedType, Array(found.size) { found[it].componentImplementation })
    }
  }
}