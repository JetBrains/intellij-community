// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.IntArrayList
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.MutableAccessor
import com.intellij.util.xmlb.PropertyAccessor
import com.intellij.util.xmlb.SerializationFilter
import org.jdom.Element
import java.lang.reflect.Constructor
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

internal class KotlinAwareBeanBinding(beanClass: Class<*>, accessor: MutableAccessor? = null) : BeanBinding(beanClass, accessor) {
  // kotlin data class constructor is never cached, because we have (and it is good) very limited number of such classes
  @Volatile
  private var constructor: Constructor<*>? = null

  // only for accessor, not field
  private fun findBindingIndex(name: String): Int {
    // accessors sorted by name
    val index = ObjectUtils.binarySearch(0, myBindings.size) { index -> myBindings[index].accessor.name.compareTo(name) }
    if (index >= 0) {
      return index
    }

    for ((i, binding) in myBindings.withIndex()) {
      val accessor = binding.accessor
      if (accessor is PropertyAccessor && accessor.getterName == name) {
        return i
      }
    }

    return -1
  }

  override fun serializeInto(o: Any, element: Element?, filter: SerializationFilter?): Element? {
    return when (o) {
      is BaseState -> serializeBaseStateInto(o, element, filter)
      else -> super.serializeInto(o, element, filter)
    }
  }

  private fun serializeBaseStateInto(o: BaseState, _element: Element?, filter: SerializationFilter?): Element? {
    var element = _element
    // order of bindings must be used, not order of properties
    var bindingIndices: IntArrayList? = null
    for (property in o.__getProperties()) {
      if (property.isEqualToDefault()) {
        continue
      }

      val propertyBindingIndex = findBindingIndex(property.name!!)
      if (propertyBindingIndex < 0) {
        logger<BaseState>().debug("cannot find binding for property ${property.name}")
        continue
      }

      if (bindingIndices == null) {
        bindingIndices = IntArrayList()
      }
      bindingIndices.add(propertyBindingIndex)
    }

    if (bindingIndices != null) {
      bindingIndices.sort()
      for (i in 0 until bindingIndices.size()) {
        element = serializePropertyInto(myBindings[bindingIndices.getQuick(i)], o, element, filter, false)
      }
    }
    return element
  }

  override fun newInstance(): Any {
    var constructor = constructor
    if (constructor != null) {
      return constructor.newInstance()
    }

    val clazz = myBeanClass
    try {
      constructor = clazz.getDeclaredConstructor()!!
      try {
        constructor.isAccessible = true
      }
      catch (ignored: SecurityException) {
        return clazz.newInstance()
      }

      val instance = constructor.newInstance()
      // cache only if constructor is valid and applicable
      this.constructor = constructor
      return instance
    }
    catch (e: RuntimeException) {
      return createUsingKotlin(clazz) ?: throw e
    }
    catch (e: NoSuchMethodException) {
      return createUsingKotlin(clazz) ?: throw e
    }
  }

  // ReflectionUtil uses another approach to do it - unreliable because located in util module, where Kotlin cannot be used.
  // Here we use Kotlin reflection and this approach is more reliable because we are prepared for future Kotlin versions.
  private fun createUsingKotlin(clazz: Class<*>): Any? {
    // if cannot create data class
    val kClass = clazz.kotlin
    val kFunction = kClass.primaryConstructor ?: kClass.constructors.first()
    try {
      kFunction.isAccessible = true
    }
    catch (ignored: SecurityException) {
    }
    return kFunction.callBy(emptyMap())
  }
}