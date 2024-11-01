// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.serviceContainer

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import org.picocontainer.ComponentAdapter
import java.io.File
import java.lang.Deprecated
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Pair
import kotlin.RuntimeException
import kotlin.Suppress
import kotlin.let

private val constructorComparator = Comparator<Constructor<*>> { c0, c1 -> c1.parameterCount - c0.parameterCount }

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val badAppLevelClasses = java.util.Set.of(
  "com.intellij.execution.executors.DefaultDebugExecutor",
  "org.apache.http.client.HttpClient",
  "org.apache.http.impl.client.CloseableHttpClient",
  "com.intellij.openapi.project.Project"
)

internal fun <T> instantiateUsingPicoContainer(aClass: Class<*>,
                                               requestorKey: Any,
                                               pluginId: PluginId,
                                               componentManager: ComponentManagerImpl): T {
  val sortedMatchingConstructors = getSortedMatchingConstructors(aClass)

  val parameterTypes: Array<Class<*>>
  val constructor: Constructor<*>
  if (sortedMatchingConstructors.size == 1) {
    constructor = sortedMatchingConstructors.first()
    parameterTypes = constructor.parameterTypes
  }
  else {
    val result = getGreediestSatisfiableConstructor(aClass = aClass,
                                                    sortedMatchingConstructors = sortedMatchingConstructors,
                                                    requestorKey = requestorKey,
                                                    pluginId = pluginId,
                                                    componentManager = componentManager)
    constructor = result.first
    parameterTypes = result.second
  }

  try {
    constructor.isAccessible = true
    if (parameterTypes.isEmpty()) {
      @Suppress("UNCHECKED_CAST")
      return constructor.newInstance() as T
    }
    else {
      var isErrorLogged = false
      val params: Array<Any?> = Array(parameterTypes.size) {
        val parameterType = parameterTypes.get(it)
        when {
          ComponentManager::class.java === parameterType -> componentManager
          parameterType === MessageBus::class.java -> componentManager.messageBus
          parameterType === CoroutineScope::class.java -> componentManager.instanceCoroutineScope(aClass)
          else -> {
            if (!isErrorLogged && !ComponentManager::class.java.isAssignableFrom(parameterType)) {
              isErrorLogged = true
              // a special unit test
              val message = doNotUseConstructorInjectionsMessage("requestorClass=${aClass.name})")
              if (componentManager.getApplication() == null) {
                LOG.warn(message)
              }
              else {
                PluginException.logPluginError(LOG, message, null, aClass)
              }
            }
            resolveInstance(componentManager = componentManager,
                            requestorKey = requestorKey,
                            requestorClass = aClass,
                            requestorConstructor = constructor,
                            expectedType = parameterType,
                            pluginId = pluginId)
          }
        }
      }
      @Suppress("UNCHECKED_CAST")
      return constructor.newInstance(*params) as T
    }
  }
  catch (e: InvocationTargetException) {
    throw e.cause ?: e
  }
  catch (e: InstantiationException) {
    throw RuntimeException("Cannot create class $aClass", e)
  }
}

internal fun isNotApplicableClass(type: Class<*>): Boolean {
  return type.isPrimitive ||
         type.isAnnotation ||
         type.isSynthetic ||
         type.isEnum ||
         type.isArray ||
         type === java.lang.String::class.java ||
         type === Class::class.java ||
         type === File::class.java ||
         type === Path::class.java ||
         java.lang.Number::class.java.isAssignableFrom(type) ||
         java.util.Collection::class.java.isAssignableFrom(type) ||
         java.util.Map::class.java.isAssignableFrom(type)
}

private fun getGreediestSatisfiableConstructor(aClass: Class<*>,
                                               sortedMatchingConstructors: Array<Constructor<*>>,
                                               requestorKey: Any,
                                               pluginId: PluginId,
                                               componentManager: ComponentManagerImpl): Pair<Constructor<*>, Array<Class<*>>> {
  var conflicts: MutableSet<Constructor<*>>? = null
  var unsatisfiableDependencyTypes: MutableSet<Array<Class<*>>>? = null
  var greediestConstructor: Constructor<*>? = null
  var greediestConstructorParameterTypes: Array<Class<*>>? = null
  var lastSatisfiableConstructorSize = -1
  var unsatisfiedDependencyType: Class<*>? = null

  val lastIndex = sortedMatchingConstructors.size - 1
  var someConstructorWasChecked = false
  for (index in 0..lastIndex) {
    val constructor = sortedMatchingConstructors[index]
    if (constructor.isSynthetic) {
      continue
    }

    var failedDependency = false
    val parameterTypes = constructor.parameterTypes

    if (lastIndex > 0 &&
        (constructor.isAnnotationPresent(NonInjectable::class.java) || constructor.isAnnotationPresent(Deprecated::class.java))) {
      continue
    }

    // first, perform fast check to ensure that assert about getComponentAdapterOfType is thrown only if the constructor is applicable
    if (parameterTypes.any(::isNotApplicableClass)) {
      continue
    }

    if (!someConstructorWasChecked && index == lastIndex) {
      // Don't call isResolvable at all - resolveInstance will do it. No need to check the only constructor in advance.
      return Pair(constructor, parameterTypes)
    }

    someConstructorWasChecked = true

    for (expectedType in parameterTypes) {
      // check whether this constructor is satisfiable
      if (isResolvable(componentManager = componentManager,
                       requestorKey = requestorKey,
                       requestorClass = aClass,
                       requestorConstructor = constructor,
                       expectedType = expectedType,
                       pluginId = pluginId)) {
        continue
      }

      if (unsatisfiableDependencyTypes == null) {
        unsatisfiableDependencyTypes = HashSet()
      }
      unsatisfiableDependencyTypes.add(parameterTypes)
      unsatisfiedDependencyType = expectedType
      failedDependency = true
      break
    }

    if (greediestConstructor != null && parameterTypes.size != lastSatisfiableConstructorSize) {
      if (conflicts.isNullOrEmpty()) {
        // we found our match (greedy and satisfied)
        return Pair(greediestConstructor, greediestConstructorParameterTypes!!)
      }
      else {
        // fits although not greedy
        conflicts.add(constructor)
      }
    }
    else if (!failedDependency && lastSatisfiableConstructorSize == parameterTypes.size) {
      // satisfied and same size as the previous one?
      if (conflicts == null) {
        conflicts = HashSet()
      }
      conflicts.add(constructor)
      greediestConstructor?.let {
        conflicts.add(it)
      }
    }
    else if (!failedDependency) {
      greediestConstructor = constructor
      greediestConstructorParameterTypes = parameterTypes
      lastSatisfiableConstructorSize = parameterTypes.size
    }
  }

  when {
    !conflicts.isNullOrEmpty() -> {
      throw PluginException("Too many satisfiable constructors: ${sortedMatchingConstructors.joinToString()}", pluginId)
    }
    greediestConstructor != null -> {
      return Pair(greediestConstructor, greediestConstructorParameterTypes!!)
    }
    !unsatisfiableDependencyTypes.isNullOrEmpty() -> {
      // second (and final) round
      throw PluginException("${aClass.name} has unsatisfied dependency: $unsatisfiedDependencyType among unsatisfiable dependencies: " +
                            "$unsatisfiableDependencyTypes where $componentManager was the leaf container being asked for dependencies.",
                            pluginId)

    }
    else -> {
      throw PluginException("The specified parameters not match any of the following constructors: " +
                            "${aClass.declaredConstructors.joinToString(separator = "\n") { it.toString() }}\n" + "for $aClass", pluginId)
    }
  }
}

// filter out all constructors that will definitely not match, optimize a list of constructors moving the longest at the beginning
private fun getSortedMatchingConstructors(componentImplementation: Class<*>): Array<Constructor<*>> {
  val declaredConstructors = componentImplementation.declaredConstructors
  declaredConstructors.sortWith(constructorComparator)
  return declaredConstructors
}

private fun resolveInstance(componentManager: ComponentManagerImpl,
                    requestorKey: Any,
                    requestorClass: Class<*>,
                    requestorConstructor: Constructor<*>,
                    expectedType: Class<*>,
                    pluginId: PluginId): Any? {
  if (isLightService(expectedType)) {
    throw PluginException("Constructor injection for light services is not supported " +
                          "(requestorClass=$requestorClass, requestedService=$expectedType)", pluginId)
  }

  val adapter = findTargetAdapter(componentManager = componentManager,
                                  expectedType = expectedType,
                                  requestorKey = requestorKey,
                                  requestorClass = requestorClass,
                                  requestorConstructor = requestorConstructor,
                                  pluginId = pluginId)
                ?: return handleUnsatisfiedDependency(componentManager, requestorClass, expectedType, pluginId)
  return when {
    adapter is HolderAdapter -> adapter.componentInstance
    componentManager.parent == null -> adapter.componentInstance
    else -> componentManager.getComponentInstance(adapter.componentKey)
  }
}

private fun handleUnsatisfiedDependency(componentManager: ComponentManagerImpl,
                                        requestorClass: Class<*>,
                                        expectedType: Class<*>,
                                        pluginId: PluginId): Any? {
  // TeamCity plugin wants DefaultDebugExecutor in constructor
  if (requestorClass.name == "com.intellij.execution.executors.DefaultDebugExecutor") {
    return componentManager.extensionArea.getExtensionPointIfRegistered<Any>("com.intellij.executor")
      ?.findExtension(requestorClass, false, ThreeState.YES)
  }

  throw PluginException(doNotUseConstructorInjectionsMessage("requestorClass=${requestorClass.name}, extensionClass=${expectedType.name}"),
                        pluginId)
}

private fun isResolvable(componentManager: ComponentManagerImpl,
                          requestorKey: Any,
                          requestorClass: Class<*>,
                          requestorConstructor: Constructor<*>,
                          expectedType: Class<*>,
                          pluginId: PluginId): Boolean {
  return expectedType === ComponentManager::class.java ||
         expectedType === CoroutineScope::class.java ||
         findTargetAdapter(componentManager = componentManager,
                           expectedType = expectedType,
                           requestorKey = requestorKey,
                           requestorClass = requestorClass,
                           requestorConstructor = requestorConstructor,
                           pluginId = pluginId) != null
}

private fun findTargetAdapter(componentManager: ComponentManagerImpl,
                              expectedType: Class<*>,
                              requestorKey: Any,
                              requestorClass: Class<*>,
                              requestorConstructor: Constructor<*>,
                              pluginId: PluginId): ComponentAdapter? {
  val byKey = componentManager.getComponentAdapter(expectedType)
  if (byKey != null && requestorKey != byKey.componentKey) {
    return byKey
  }

  val className = expectedType.name
  if (componentManager.parent == null) {
    if (badAppLevelClasses.contains(className)) {
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
  return componentManager.getComponentAdapterOfType(expectedType) ?: componentManager.parent?.getComponentAdapterOfType(expectedType)
}