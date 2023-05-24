// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.serviceContainer

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.lang.Deprecated
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Comparator
import kotlin.Pair
import kotlin.RuntimeException
import kotlin.Suppress
import kotlin.let

internal fun <T> instantiateUsingPicoContainer(aClass: Class<*>,
                                               requestorKey: Any,
                                               pluginId: PluginId,
                                               componentManager: ComponentManagerImpl,
                                               parameterResolver: ConstructorParameterResolver): T {
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
                                                    componentManager = componentManager,
                                                    parameterResolver = parameterResolver,
                                                    isExtensionSupported = false)
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
            parameterResolver.resolveInstance(componentManager = componentManager,
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
  return type.isPrimitive || type.isEnum || type.isArray ||
         java.util.Collection::class.java.isAssignableFrom(type) ||
         java.util.Map::class.java.isAssignableFrom(type) ||
         type === java.lang.String::class.java ||
         type === File::class.java ||
         type === Path::class.java
}

private fun getGreediestSatisfiableConstructor(aClass: Class<*>,
                                               sortedMatchingConstructors: Array<Constructor<*>>,
                                               requestorKey: Any,
                                               pluginId: PluginId,
                                               componentManager: ComponentManagerImpl,
                                               parameterResolver: ConstructorParameterResolver,
                                               isExtensionSupported: Boolean): Pair<Constructor<*>, Array<Class<*>>> {
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
      if (parameterResolver.isResolvable(componentManager = componentManager,
                                         requestorKey = requestorKey,
                                         requestorClass = aClass,
                                         requestorConstructor = constructor,
                                         expectedType = expectedType,
                                         pluginId = pluginId,
                                         isExtensionSupported = isExtensionSupported)) {
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
      if (isExtensionSupported) {
        throw PluginException("${aClass.name} has unsatisfied dependency: $unsatisfiedDependencyType among unsatisfiable dependencies: " +
                              "$unsatisfiableDependencyTypes where $componentManager was the leaf container being asked for dependencies.", pluginId)
      }
      else {
        return getGreediestSatisfiableConstructor(aClass, sortedMatchingConstructors, requestorKey, pluginId, componentManager, parameterResolver, true)
      }
    }
    else -> {
      throw PluginException("The specified parameters not match any of the following constructors: " +
                            "${aClass.declaredConstructors.joinToString(separator = "\n") { it.toString() }}\n" + "for $aClass", pluginId)
    }
  }
}

private val constructorComparator = Comparator<Constructor<*>> { c0, c1 -> c1.parameterCount - c0.parameterCount }

// filter out all constructors that will definitely not match, optimize a list of constructors moving the longest at the beginning
private fun getSortedMatchingConstructors(componentImplementation: Class<*>): Array<Constructor<*>> {
  val declaredConstructors = componentImplementation.declaredConstructors
  declaredConstructors.sortWith(constructorComparator)
  return declaredConstructors
}