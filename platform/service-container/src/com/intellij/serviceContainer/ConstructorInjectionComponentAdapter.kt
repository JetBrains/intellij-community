// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.pico.DefaultPicoContainer
import gnu.trove.THashSet
import org.picocontainer.*
import org.picocontainer.defaults.AmbiguousComponentResolutionException
import org.picocontainer.defaults.CyclicDependencyException
import org.picocontainer.defaults.TooManySatisfiableConstructorsException
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

private val ourGuard = ThreadLocal<MutableSet<Class<*>>>()

internal class ConstructorInjectionComponentAdapter(componentKey: Any, implementation: Class<*>, private val componentManager: PlatformComponentManagerImpl) : InstantiatingComponentAdapter(componentKey, implementation) {
  override fun getComponentInstance(container: PicoContainer): Any {
    return instantiateGuarded(componentImplementation, componentKey, componentManager, ConstructorParameterResolver.INSTANCE, componentImplementation)
  }
}

internal fun <T> instantiateGuarded(aClass: Class<*>, key: Any, componentManager: PlatformComponentManagerImpl, defaultResolver: ConstructorParameterResolver, stackFrame: Class<*>): T {
  var currentStack = ourGuard.get()
  if (currentStack == null) {
    currentStack = ContainerUtil.newIdentityTroveSet()
    ourGuard.set(currentStack)
  }

  if (!currentStack.add(aClass)) {
    throw CyclicDependencyException(stackFrame)
  }

  @Suppress("UNCHECKED_CAST")
  try {
    return doGetComponentInstance(aClass, key, componentManager, defaultResolver) as T
  }
  catch (e: CyclicDependencyException) {
    e.push(stackFrame)
    throw e
  }
  finally {
    currentStack.remove(aClass)
  }
}

private fun doGetComponentInstance(aClass: Class<*>, requestorKey: Any, componentManager: PlatformComponentManagerImpl, defaultResolver: ConstructorParameterResolver): Any {
  val constructor = try {
    getGreediestSatisfiableConstructor(aClass, requestorKey, componentManager, defaultResolver)
  }
  catch (e: AmbiguousComponentResolutionException) {
    e.setComponent(aClass)
    throw e
  }

  try {
    constructor.isAccessible = true
    val parameterTypes = constructor.parameterTypes
    return constructor.newInstance(*Array(parameterTypes.size) {
      defaultResolver.resolveInstance(componentManager, requestorKey, parameterTypes[it])
    })
  }
  catch (e: InvocationTargetException) {
    throw e.cause ?: e
  }
}

private fun getGreediestSatisfiableConstructor(aClass: Class<*>, requestorKey: Any, componentManager: PlatformComponentManagerImpl, defaultResolver: ConstructorParameterResolver): Constructor<*> {
  var conflicts: MutableSet<Constructor<*>>? = null
  var unsatisfiableDependencyTypes: MutableSet<Array<Class<*>>>? = null
  val sortedMatchingConstructors = getSortedMatchingConstructors(aClass)
  var greediestConstructor: Constructor<*>? = null
  var lastSatisfiableConstructorSize = -1
  var unsatisfiedDependencyType: Class<*>? = null
  loop@ for (constructor in sortedMatchingConstructors) {
    var failedDependency = false
    val parameterTypes = constructor.parameterTypes

    for (expectedType in parameterTypes) {
      if (expectedType.isPrimitive || expectedType.isEnum || expectedType.isArray || Collection::class.java.isAssignableFrom(expectedType)) {
        continue@loop
      }

      // check whether this constructor is satisfiable
      if (defaultResolver.isResolvable(componentManager, requestorKey, expectedType)) {
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
        return greediestConstructor
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
      lastSatisfiableConstructorSize = parameterTypes.size
    }
  }

  when {
    !conflicts.isNullOrEmpty() -> throw TooManySatisfiableConstructorsException(aClass, conflicts)
    greediestConstructor != null -> return greediestConstructor
    !unsatisfiableDependencyTypes.isNullOrEmpty() -> throw PicoIntrospectionException("$requestorKey has unsatisfied dependency: $unsatisfiedDependencyType among unsatisfiable dependencies: " +
                                                                                      "$unsatisfiableDependencyTypes where $componentManager was the leaf container being asked for dependencies.")
    else -> {
      throw PicoInitializationException("The specified parameters not match any of the following constructors: " +
                                        "${aClass.declaredConstructors.joinToString(separator = "\n") { it.toString() }}\n" +
                                        "for '$aClass'")
    }
  }
}

private val constructorComparator = Comparator<Constructor<*>> { c0, c1 -> c1.parameterCount - c0.parameterCount }

// filter out all constructors that will definitely not match
// optimize list of constructors moving the longest at the beginning
private fun getSortedMatchingConstructors(componentImplementation: Class<*>): Array<Constructor<*>> {
  val declaredConstructors = componentImplementation.declaredConstructors
  declaredConstructors.sortWith(constructorComparator)
  return declaredConstructors
}

abstract class InstantiatingComponentAdapter(private val componentKey: Any, private val componentImplementation: Class<*>) : ComponentAdapter {
  override fun getComponentKey() = componentKey

  override fun getComponentImplementation() = componentImplementation

  override fun verify(container: PicoContainer) {
    throw UnsupportedOperationException()
  }

  override fun accept(visitor: PicoVisitor) {
    throw UnsupportedOperationException()
  }
}

internal open class ConstructorParameterResolver {
  companion object {
    val INSTANCE = ConstructorParameterResolver()
  }

  open fun isResolvable(componentManager: PlatformComponentManagerImpl, requestorKey: Any, expectedType: Class<*>): Boolean {
    return resolveAdapter(componentManager, requestorKey, expectedType) != null
  }

  open fun resolveInstance(componentManager: PlatformComponentManagerImpl, requestorKey: Any, expectedType: Class<*>): Any? {
    val componentAdapter = resolveAdapter(componentManager, requestorKey, expectedType) ?: return null
    return componentManager.picoContainer.getComponentInstance(componentAdapter.componentKey)
  }

  private fun resolveAdapter(componentManager: PlatformComponentManagerImpl, requestorKey: Any, expectedType: Class<*>): ComponentAdapter? {
    val result = getTargetAdapter(componentManager.picoContainer, expectedType, requestorKey) ?: return null
    if (expectedType.isAssignableFrom(result.componentImplementation)) {
      return result
    }
    return null
  }

  private fun getTargetAdapter(container: DefaultPicoContainer, expectedType: Class<*>, excludeKey: Any): ComponentAdapter? {
    val byKey = container.getComponentAdapter(expectedType)
    if (byKey != null && excludeKey != byKey.componentKey) {
      return byKey
    }

    val found = container.getComponentAdaptersOfType(expectedType)
    found.removeIf { it.componentKey == excludeKey }
    return when {
      found.size == 0 -> container.parent?.getComponentAdapterOfType(expectedType)
      found.size == 1 -> found[0]
      else -> throw AmbiguousComponentResolutionException(expectedType, Array(found.size) { found[it].componentImplementation })
    }
  }
}