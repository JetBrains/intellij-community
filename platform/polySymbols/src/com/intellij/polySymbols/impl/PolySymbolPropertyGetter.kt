// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil
import java.lang.reflect.Field
import java.lang.reflect.Method

object PolySymbolPropertyGetter {

  private val accessorsMap: MutableMap<Class<out PolySymbol>, Map<PolySymbolProperty<*>, (PolySymbol) -> Any?>> =
    ContainerUtil.createConcurrentWeakMap()

  fun <T : Any> get(symbol: PolySymbol, property: PolySymbolProperty<T>): T? =
    accessorsMap.computeIfAbsent(symbol::class.java) {
      buildPropertiesMap(it)
    }[property]?.let { property.tryCast(it(symbol)) }

  private fun buildPropertiesMap(symbolClass: Class<out PolySymbol>): Map<PolySymbolProperty<*>, (PolySymbol) -> Any?> {
    val toVisit = ArrayDeque<Class<*>>()
    toVisit.add(symbolClass)
    val visited = hashSetOf<Class<*>>()

    val result = mutableMapOf<PolySymbolProperty<*>, (PolySymbol) -> Any?>()

    while (toVisit.isNotEmpty()) {
      val clazz = toVisit.removeFirst()
      if (!visited.add(clazz)) continue

      for (field in clazz.declaredFields) {
        val property = getProperty(field.getDeclaredAnnotation(PolySymbol.Property::class.java))
                       ?: continue
        addField(property, field, result, clazz)
      }
      for (method in clazz.declaredMethods) {
        val property = getProperty(method.getDeclaredAnnotation(PolySymbol.Property::class.java))
                       ?: continue
        addMethod(property, method, result, clazz)
      }
      clazz.superclass?.let { toVisit.add(it) }
      toVisit.addAll(clazz.interfaces)
    }
    return result
  }

  private fun addField(
    property: PolySymbolProperty<*>?,
    field: Field,
    result: MutableMap<PolySymbolProperty<*>, (PolySymbol) -> Any?>,
    clazz: Class<*>,
  ) {
    if (property == null || result.containsKey(property)) return
    if (property.type.objectType.isAssignableFrom(field.type.objectType)) {
      field.isAccessible = true
      result[property] = { field.get(it) }
    }
    else {
      thisLogger().error("PolySymbol property ${property.name} of type ${property.type} is not assignable from field type ${field.type} of ${clazz.name}.${field.name}")
    }
  }

  private fun addMethod(
    property: PolySymbolProperty<*>?,
    method: Method,
    result: MutableMap<PolySymbolProperty<*>, (PolySymbol) -> Any?>,
    clazz: Class<*>,
  ) {
    if (property == null || result.containsKey(property)) return
    val actualMethod = if (method.name.endsWith($$"$annotations"))
      try {
        clazz.getDeclaredMethod(method.name.removeSuffix($$"$annotations"))
      }
      catch (_: NoSuchMethodException) {
        if (method.name.startsWith("get")) {
          try {
            val field = clazz.getDeclaredField(
              method.name.removeSuffix($$"$annotations").removePrefix("get").let { StringUtil.decapitalize(it) }
            )
            addField(property, field, result, clazz)
            return
          }
          catch (_: NoSuchFieldException) {
          }
        }
        method
      }
    else
      method
    if (actualMethod.parameterCount != 0) {
      thisLogger().error("Method ${clazz.name}.${actualMethod.name} annotated with @PolySymbol.Property should not have parameters.")
    }
    else if (property.type.objectType.isAssignableFrom(actualMethod.returnType.objectType)) {
      actualMethod.isAccessible = true
      result[property] = { actualMethod.invoke(it) }
    }
    else {
      thisLogger().error("PolySymbol property ${property.name} of type ${property.type} is not assignable from return type ${actualMethod.returnType} of ${clazz.name}.${actualMethod.name}()")
    }
  }

  private val Class<*>.objectType: Class<*>
    get() = if (isPrimitive) kotlin.javaObjectType else this

  private fun getProperty(annotation: PolySymbol.Property?): PolySymbolProperty<*>? {
    val propertyClass = annotation?.property ?: return null
    @Suppress("NO_REFLECTION_IN_CLASS_PATH")
    return propertyClass.objectInstance.asSafely<PolySymbolProperty<*>>()
           ?: try {
             propertyClass.java.getDeclaredConstructor()
           }
           catch (_: NoSuchMethodException) {
             null
           }
             ?.newInstance()
             ?.asSafely<PolySymbolProperty<*>>()
  }
}
