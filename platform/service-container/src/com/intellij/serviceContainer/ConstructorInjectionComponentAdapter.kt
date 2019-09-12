// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ComponentManager
import com.intellij.util.pico.DefaultPicoContainer
import gnu.trove.THashSet
import org.picocontainer.ComponentAdapter
import org.picocontainer.PicoInitializationException
import org.picocontainer.PicoIntrospectionException
import org.picocontainer.defaults.AmbiguousComponentResolutionException
import org.picocontainer.defaults.TooManySatisfiableConstructorsException
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path

internal fun <T> instantiateUsingPicoContainer(aClass: Class<*>, requestorKey: Any, componentManager: PlatformComponentManagerImpl, parameterResolver: ConstructorParameterResolver): T {
  val sortedMatchingConstructors = getSortedMatchingConstructors(aClass)

  val parameterTypes: Array<Class<*>>
  val constructor: Constructor<*>
  if (sortedMatchingConstructors.size == 1) {
    constructor = sortedMatchingConstructors.first()
    parameterTypes = constructor.parameterTypes
  }
  else {
    val result = getGreediestSatisfiableConstructor(aClass, sortedMatchingConstructors, requestorKey, componentManager, parameterResolver)
    constructor = result.first
    parameterTypes = result.second
  }

  try {
    constructor.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*Array(parameterTypes.size) {
      parameterResolver.resolveInstance(componentManager, requestorKey, aClass, constructor, parameterTypes[it])
    }) as T
  }
  catch (e: InvocationTargetException) {
    throw e.cause ?: e
  }
}

private fun isNotApplicableClass(type: Class<*>): Boolean {
  return type.isPrimitive || type.isEnum || type.isArray ||
         Collection::class.java.isAssignableFrom(type) ||
         Map::class.java.isAssignableFrom(type) ||
         type === File::class.java || type === Path::class.java
}

private fun getGreediestSatisfiableConstructor(aClass: Class<*>,
                                               sortedMatchingConstructors: Array<Constructor<*>>,
                                               requestorKey: Any,
                                               componentManager: PlatformComponentManagerImpl,
                                               parameterResolver: ConstructorParameterResolver): Pair<Constructor<*>, Array<Class<*>>> {
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
      if (parameterResolver.isResolvable(componentManager, requestorKey, aClass, constructor, expectedType)) {
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
      throw PicoIntrospectionException("${aClass.name} has unsatisfied dependency: $unsatisfiedDependencyType among unsatisfiable dependencies: " +
                                       "$unsatisfiableDependencyTypes where $componentManager was the leaf container being asked for dependencies.")
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

internal abstract class ConstructorParameterResolver {
  open fun isResolvable(componentManager: PlatformComponentManagerImpl,
                        requestorKey: Any,
                        requestorClass: Class<*>,
                        requestorConstructor: Constructor<*>,
                        expectedType: Class<*>): Boolean {
    return expectedType === ComponentManager::class.java || resolveAdapter(componentManager, requestorKey, requestorClass, requestorConstructor, expectedType) != null
  }

  open fun resolveInstance(componentManager: PlatformComponentManagerImpl,
                           requestorKey: Any,
                           requestorClass: Class<*>,
                           requestorConstructor: Constructor<*>,
                           expectedType: Class<*>): Any? {
    if (expectedType === ComponentManager::class.java) {
      return componentManager
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

  protected open fun handleUnsatisfiedDependency(componentManager: PlatformComponentManagerImpl, requestorClass: Class<*>, expectedType: Class<*>): Any? {
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
      return null;
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