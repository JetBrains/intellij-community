// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.extensions.PluginId
import gnu.trove.THashSet
import org.picocontainer.PicoInitializationException
import org.picocontainer.PicoIntrospectionException
import org.picocontainer.defaults.TooManySatisfiableConstructorsException
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path

internal fun <T> instantiateUsingPicoContainer(aClass: Class<*>,
                                               requestorKey: Any,
                                               pluginId: PluginId?,
                                               componentManager: PlatformComponentManagerImpl,
                                               parameterResolver: ConstructorParameterResolver): T {
  val sortedMatchingConstructors = getSortedMatchingConstructors(aClass)

  val parameterTypes: Array<Class<*>>
  val constructor: Constructor<*>
  if (sortedMatchingConstructors.size == 1) {
    constructor = sortedMatchingConstructors.first()
    parameterTypes = constructor.parameterTypes
  }
  else {
    // first round - try to find without extensions (because class can have several constructors - we cannot resolve using extension area at first place,
    // because some another constructor can be satisfiable
    val result = getGreediestSatisfiableConstructor(aClass, sortedMatchingConstructors, requestorKey, pluginId, componentManager, parameterResolver, false)
    constructor = result.first
    parameterTypes = result.second
  }

  try {
    constructor.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*Array(parameterTypes.size) {
      parameterResolver.resolveInstance(componentManager, requestorKey, aClass, constructor, parameterTypes.get(it), pluginId)
    }) as T
  }
  catch (e: InvocationTargetException) {
    throw e.cause ?: e
  }
}

internal fun isNotApplicableClass(type: Class<*>): Boolean {
  return type.isPrimitive || type.isEnum || type.isArray ||
         Collection::class.java.isAssignableFrom(type) ||
         Map::class.java.isAssignableFrom(type) ||
         type === File::class.java || type === Path::class.java
}

private fun getGreediestSatisfiableConstructor(aClass: Class<*>,
                                               sortedMatchingConstructors: Array<Constructor<*>>,
                                               requestorKey: Any,
                                               pluginId: PluginId?,
                                               componentManager: PlatformComponentManagerImpl,
                                               parameterResolver: ConstructorParameterResolver,
                                               isExtensionSupported: Boolean): Pair<Constructor<*>, Array<Class<*>>> {
  var conflicts: MutableSet<Constructor<*>>? = null
  var unsatisfiableDependencyTypes: MutableSet<Array<Class<*>>>? = null
  var greediestConstructor: Constructor<*>? = null
  var greediestConstructorParameterTypes: Array<Class<*>>? = null
  var lastSatisfiableConstructorSize = -1
  var unsatisfiedDependencyType: Class<*>? = null

  loop@ for (constructor in sortedMatchingConstructors) {
    if (constructor.isSynthetic) {
      continue
    }

    if (sortedMatchingConstructors.size > 1 &&
        (constructor.isAnnotationPresent(java.lang.Deprecated::class.java) || constructor.isAnnotationPresent(NonInjectable::class.java))) {
      continue
    }

    var failedDependency = false
    val parameterTypes = constructor.parameterTypes

    for (expectedType in parameterTypes) {
      if (isNotApplicableClass(expectedType)) {
        continue@loop
      }

      // check whether this constructor is satisfiable
      if (parameterResolver.isResolvable(componentManager, requestorKey, aClass, constructor, expectedType, pluginId, isExtensionSupported)) {
        continue
      }

      if (unsatisfiableDependencyTypes == null) {
        unsatisfiableDependencyTypes = THashSet()
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
      // satisfied and same size as previous one?
      if (conflicts == null) {
        conflicts = THashSet()
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
      throw TooManySatisfiableConstructorsException(aClass, conflicts)
    }
    greediestConstructor != null -> {
      return Pair(greediestConstructor, greediestConstructorParameterTypes!!)
    }
    !unsatisfiableDependencyTypes.isNullOrEmpty() -> {
      // second (and final) round
      if (isExtensionSupported) {
        throw PicoIntrospectionException("${aClass.name} has unsatisfied dependency: $unsatisfiedDependencyType among unsatisfiable dependencies: " +
                                         "$unsatisfiableDependencyTypes where $componentManager was the leaf container being asked for dependencies.")
      }
      else {
        return getGreediestSatisfiableConstructor(aClass, sortedMatchingConstructors, requestorKey, pluginId, componentManager, parameterResolver, true)
      }
    }
    else -> {
      throw createNoSatisfiableConstructorError(aClass)
    }
  }
}

private fun createNoSatisfiableConstructorError(aClass: Class<*>): RuntimeException {
  return PicoInitializationException("The specified parameters not match any of the following constructors: " +
                                     "${aClass.declaredConstructors.joinToString(separator = "\n") { it.toString() }}\n" +
                                     "for $aClass")
}

private val constructorComparator = Comparator<Constructor<*>> { c0, c1 -> c1.parameterCount - c0.parameterCount }

// filter out all constructors that will definitely not match
// optimize list of constructors moving the longest at the beginning
private fun getSortedMatchingConstructors(componentImplementation: Class<*>): Array<Constructor<*>> {
  val declaredConstructors = componentImplementation.declaredConstructors
  declaredConstructors.sortWith(constructorComparator)
  return declaredConstructors
}